package com.example.auditpanel.service;

import com.example.auditpanel.model.BankingTransactionEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects banking anomalies (ANO-001..054) from BankingTransactionEvent instances.
 *
 * Each detected anomaly is a pipe-separated string: ANO-XXX|SEVERIDAD|CATEGORIA|detalle
 *
 * Anomalias implementadas (detectables desde transacciones.log sin cruzar sesiones.log):
 *   ANO-001  Smurfing por importe (TRANSFERENCIA en [9000,9999])
 *   ANO-002  Smurfing por multiples destinos (>=5 destinos distintos en 24h)
 *   ANO-003  Cuenta mula receptora (>=5 origenes distintos en 6h)
 *   ANO-004  Transito rapido (recibe y reenvia importe alto en <60 min)
 *   ANO-006  Importes redondos repetidos (mismo multiplo de 1000, >=3 veces en 7 dias)
 *   ANO-007  Transferencia grande + CIERRE_CUENTA en <48h
 *   ANO-010  Microdeposito precursor (<0.10 EUR)
 *   ANO-025  Velocidad: >10 transacciones en 5 minutos
 *   ANO-026  Cajeros geográficamente imposibles (<90 min entre ciudades distintas)
 *   ANO-027  Maximo diario en cajero 7 dias consecutivos
 *   ANO-028  Pagos simultaneos en comercios fisicos distintos (<2 min)
 *   ANO-029  Rafaga de pagos online (>=5 PAGO_ONLINE en <2 min)
 *   ANO-031  Timestamp ausente
 *   ANO-032  Importe negativo o cero
 *   ANO-033  transaction_id duplicado
 *   ANO-034  ip_origen ausente
 *   ANO-035  Operacion sin sesion_id (posible bypass de autenticacion)
 *   ANO-036  Gap temporal >6h en cuenta (posible borrado de registros)
 *   ANO-037  Velocidad inhumana entre eventos (<500ms en misma sesion)
 *   ANO-038  Divisa no permitida
 *   ANO-039  Importe con mas de 2 decimales
 *   ANO-040  Concepto con SQL/XSS
 *   ANO-042  Primera transferencia a destino nuevo por importe alto (>3000 EUR)
 *   ANO-045  Supera 10.000 EUR sin alerta regulatoria (Ley 10/2010)
 *   ANO-047  Transferencia a pais bajo embargo/sancion internacional
 */
@Service
public class BankingAnomalyDetectorService {

    // ------------------------------------------------------------------
    // Inner record for stateful tracking
    // ------------------------------------------------------------------
    private static final class TxEntry {
        final long millis;
        final String tag;    // counterparty ID, city, terminal, etc.
        final double importe;

        TxEntry(long millis, String tag, double importe) {
            this.millis  = millis;
            this.tag     = tag;
            this.importe = importe;
        }
    }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final Set<String> VALID_CURRENCIES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("EUR", "USD", "GBP", "CHF")));

    private static final Set<String> EMBARGOED_COUNTRIES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("RU", "KP", "IR", "BY", "SY", "CU", "VE", "SD")));

    private static final List<String> SQL_KEYWORDS =
            Arrays.asList("--", "DROP", "SELECT", "<script>", "OR 1=1", "UNION", "'; ");

    // ANO-001 smurfing range
    private static final double SMURFING_LOW  = 9_000.0;
    private static final double SMURFING_HIGH = 9_999.0;

    // ANO-010 microdeposit
    private static final double MICRODEPOSIT_MAX = 0.10;

    // ANO-025 velocity: >10 tx in 5 min
    private static final long VELOCITY_WINDOW_MS  = 5L  * 60_000L;
    private static final int  VELOCITY_THRESHOLD  = 10;

    // ANO-002 smurfing multiple destinations: >=5 unique in 24h
    private static final int SMURFING_DEST_THRESHOLD = 5;

    // ANO-003 cuenta mula: >=5 unique origins in 6h
    private static final long MULA_WINDOW_MS       = 6L * 60L * 60_000L;
    private static final int  MULA_ORIGEN_THRESHOLD = 5;

    // ANO-004 transito rapido: receive then send in <60 min, importe >10.000
    private static final long  TRANSITO_WINDOW_MS    = 60L * 60_000L;
    private static final double TRANSITO_MIN_IMPORTE  = 10_000.0;
    private static final double TRANSITO_TOLERANCE    = 0.05; // 5% tolerance

    // ANO-006 importes redondos: same round amount >=3 times in 7 days
    private static final long ROUND_WINDOW_MS  = 7L * 24L * 60L * 60_000L;
    private static final int  ROUND_THRESHOLD  = 3;

    // ANO-007 cierre cuenta: large transfer (>10.000) + CIERRE_CUENTA in <48h
    private static final long   CIERRE_WINDOW_MS        = 48L * 60L * 60_000L;
    private static final double CIERRE_MIN_TRANSFERENCIA = 10_000.0;

    // ANO-026 cajeros imposibles: different city in <90 min
    private static final long ATM_TRAVEL_MIN_MS = 90L * 60_000L;

    // ANO-028 pagos fisicos simultaneos: different terminal in <2 min
    private static final long FISICO_WINDOW_MS = 2L * 60_000L;

    // ANO-029 rafaga pagos online: >=5 PAGO_ONLINE in <2 min
    private static final long PAGO_ONLINE_WINDOW_MS   = 2L * 60_000L;
    private static final int  PAGO_ONLINE_THRESHOLD   = 5;

    // ANO-036 gap temporal: >6h gap in same account
    private static final long GAP_THRESHOLD_MS = 6L * 60L * 60_000L;

    // ANO-037 bot speed: >=3 events with <500ms between them in same session
    private static final long BOT_SPEED_MS    = 500L;
    private static final int  BOT_SPEED_COUNT = 3;

    // ANO-042 new destination: importe >3.000 to a never-seen destination
    private static final double NEW_DEST_MIN_IMPORTE = 3_000.0;

    // ANO-045 regulatory threshold (Ley 10/2010)
    private static final double REGULATORY_THRESHOLD = 10_000.0;

    // ------------------------------------------------------------------
    // State — in-memory, persists across calls within the same bean lifetime
    // ------------------------------------------------------------------

    // ANO-033: seen transaction IDs
    private final Set<String> seenTransactionIds = ConcurrentHashMap.newKeySet();

    // ANO-025: per-account timestamp lists for velocity check
    private final Map<String, List<String>> cuentaTimestamps = new ConcurrentHashMap<>();

    // ANO-002: per-account -> per-day -> unique destinations
    private final Map<String, Map<String, Set<String>>> smurfingDestByDay = new ConcurrentHashMap<>();

    // ANO-003: per-destination -> recent incoming origins (with timestamp)
    private final Map<String, Deque<TxEntry>> mulaIncomingHistory = new ConcurrentHashMap<>();

    // ANO-004: per-account -> recent received amounts (appeared as cuenta_destino)
    private final Map<String, Deque<TxEntry>> cuentaRecepciones = new ConcurrentHashMap<>();

    // ANO-006: key="cuenta@importe" -> list of timestamps
    private final Map<String, List<Long>> importesRedondosHits = new ConcurrentHashMap<>();

    // ANO-007: per-account -> recent large transfers (to detect CIERRE_CUENTA)
    private final Map<String, Deque<TxEntry>> grandesTransferencias = new ConcurrentHashMap<>();

    // ANO-026: per-account -> recent ATM events (tag = city)
    private final Map<String, Deque<TxEntry>> cajeroHistory = new ConcurrentHashMap<>();

    // ANO-027: key="cuenta@date" -> list of ATM importes that day
    private final Map<String, List<Double>> cajeroImportesPorDia = new ConcurrentHashMap<>();

    // ANO-028: per-account -> recent PAGO_COMERCIO events (tag = terminal ID)
    private final Map<String, Deque<TxEntry>> fisicoPagoHistory = new ConcurrentHashMap<>();

    // ANO-029: per-account -> recent PAGO_ONLINE timestamps
    private final Map<String, Deque<Long>> pagoOnlineTimestamps = new ConcurrentHashMap<>();

    // ANO-036: per-account -> last event timestamp (millis)
    private final Map<String, Long> ultimoTimestampPorCuenta = new ConcurrentHashMap<>();

    // ANO-037: per-session -> recent event timestamps
    private final Map<String, Deque<Long>> sesionEventos = new ConcurrentHashMap<>();

    // ANO-042: per-account -> set of known destination accounts
    private final Map<String, Set<String>> destinosConocidos = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Main detection method
    // ------------------------------------------------------------------

    /**
     * Analyse a single BankingTransactionEvent and return detected anomaly strings.
     * Each element: "ANO-XXX|SEVERIDAD|CATEGORIA|detalle"
     */
    public List<String> detectBanking(BankingTransactionEvent event) {
        List<String> anomalies = new ArrayList<>();
        if (event == null) return anomalies;

        long nowMs = parseTimestampMillis(event.getTimestamp());

        // ============================================================
        //  STATELESS CHECKS (no history needed)
        // ============================================================

        // ANO-031 — timestamp ausente
        if (isBlank(event.getTimestamp())) {
            anomalies.add(ano("ANO-031", "CRITICA", "Integridad logs",
                    "Timestamp ausente en transaccion " + safe(event.getTransactionId())));
        }

        // ANO-032 — importe negativo o cero
        if (event.getImporte() != null && event.getImporte() <= 0.0) {
            anomalies.add(ano("ANO-032", "CRITICA", "Integridad logs",
                    "Importe no positivo (" + event.getImporte()
                            + ") en cuenta " + safe(event.getCuentaOrigen())));
        }

        // ANO-034 — ip_origen ausente
        if (isBlank(event.getIpOrigen())) {
            anomalies.add(ano("ANO-034", "ALTA", "Autenticacion",
                    "ip_origen ausente en transaccion " + safe(event.getTransactionId())));
        }

        // ANO-035 — sesion_id ausente (operacion sin autenticacion previa)
        if (isBlank(event.getSesionId())) {
            anomalies.add(ano("ANO-035", "CRITICA", "Integridad logs",
                    "Operacion sin sesion_id en transaccion "
                            + safe(event.getTransactionId()) + " — posible bypass de autenticacion"));
        }

        // ANO-038 — divisa no permitida
        if (!isBlank(event.getDivisa())
                && !VALID_CURRENCIES.contains(event.getDivisa().toUpperCase())) {
            anomalies.add(ano("ANO-038", "MEDIA", "Normativa",
                    "Divisa no permitida: " + event.getDivisa()
                            + " en cuenta " + safe(event.getCuentaOrigen())));
        }

        // ANO-039 — importe con mas de 2 decimales
        if (event.getImporte() != null) {
            try {
                int scale = new BigDecimal(event.getImporte().toString())
                        .stripTrailingZeros().scale();
                if (scale > 2) {
                    anomalies.add(ano("ANO-039", "MEDIA", "Integridad logs",
                            "Importe con " + scale + " decimales: " + event.getImporte()
                                    + " en cuenta " + safe(event.getCuentaOrigen())));
                }
            } catch (NumberFormatException ignored) { /* importe ya es Double */ }
        }

        // ANO-040 — concepto con SQL/XSS
        if (!isBlank(event.getConcepto())) {
            String upper = event.getConcepto().toUpperCase();
            for (String kw : SQL_KEYWORDS) {
                if (upper.contains(kw.toUpperCase())) {
                    anomalies.add(ano("ANO-040", "CRITICA", "Fraude externo",
                            "Concepto contiene patron de inyeccion '"
                                    + kw + "' en cuenta " + safe(event.getCuentaOrigen())));
                    break;
                }
            }
        }

        // ANO-045 — supera 10.000 EUR sin alerta regulatoria (Ley 10/2010)
        if (event.getImporte() != null && event.getImporte() >= REGULATORY_THRESHOLD) {
            String tipo = event.getTipoOperacion();
            if ("DEPOSITO".equalsIgnoreCase(tipo) || "RETIRO_CAJERO".equalsIgnoreCase(tipo)) {
                anomalies.add(ano("ANO-045", "CRITICA", "Normativa",
                        "Operacion de " + event.getImporte() + " EUR (" + tipo
                                + ") supera umbral de 10.000 EUR — reporte obligatorio Ley 10/2010"
                                + " — cuenta " + safe(event.getCuentaOrigen())));
            }
        }

        // ANO-047 — pais bajo embargo/sancion internacional
        if (!isBlank(event.getPaisDestino())
                && EMBARGOED_COUNTRIES.contains(event.getPaisDestino().toUpperCase())) {
            anomalies.add(ano("ANO-047", "CRITICA", "Normativa",
                    "Transferencia a pais bajo embargo internacional: "
                            + event.getPaisDestino()
                            + " desde cuenta " + safe(event.getCuentaOrigen())));
        }

        // ============================================================
        //  STATEFUL CHECKS (require valid timestamp)
        // ============================================================

        if (nowMs > 0) {

            // ANO-033 — transaction_id duplicado
            if (!isBlank(event.getTransactionId())) {
                if (!seenTransactionIds.add(event.getTransactionId())) {
                    anomalies.add(ano("ANO-033", "ALTA", "Fraude externo",
                            "transaction_id duplicado: " + event.getTransactionId()
                                    + " (posible ataque replay)"));
                }
            }

            // ANO-025 — velocidad >10 tx en 5 min
            if (!isBlank(event.getCuentaOrigen())) {
                checkVelocidad(event, nowMs, anomalies);
            }

            // ANO-001 — smurfing por importe
            if (event.getImporte() != null
                    && "TRANSFERENCIA".equalsIgnoreCase(event.getTipoOperacion())
                    && event.getImporte() >= SMURFING_LOW
                    && event.getImporte() <= SMURFING_HIGH) {
                anomalies.add(ano("ANO-001", "ALTA", "Blanqueo",
                        "Smurfing por importe: TRANSFERENCIA de " + event.getImporte()
                                + " EUR (rango [9000,9999]) desde cuenta "
                                + safe(event.getCuentaOrigen())));
            }

            // ANO-010 — microdeposito precursor
            if (event.getImporte() != null
                    && event.getImporte() > 0.0
                    && event.getImporte() < MICRODEPOSIT_MAX) {
                anomalies.add(ano("ANO-010", "MEDIA", "Blanqueo",
                        "Microdeposito precursor: importe " + event.getImporte()
                                + " EUR (< 0.10) desde cuenta "
                                + safe(event.getCuentaOrigen())));
            }

            // ANO-002 — smurfing por multiples destinos
            if ("TRANSFERENCIA".equalsIgnoreCase(event.getTipoOperacion())
                    && !isBlank(event.getCuentaOrigen())
                    && !isBlank(event.getCuentaDestino())) {
                checkSmurfingMultipleDestinos(event, nowMs, anomalies);
            }

            // ANO-003 — cuenta mula receptora
            if ("TRANSFERENCIA".equalsIgnoreCase(event.getTipoOperacion())
                    && !isBlank(event.getCuentaDestino())
                    && !isBlank(event.getCuentaOrigen())) {
                checkCuentaMulaReceptora(event, nowMs, anomalies);
            }

            // ANO-004 — transito rapido
            if (!isBlank(event.getCuentaOrigen()) || !isBlank(event.getCuentaDestino())) {
                checkTransitoRapido(event, nowMs, anomalies);
            }

            // ANO-006 — importes redondos repetidos
            if (event.getImporte() != null && event.getImporte() > 0
                    && !isBlank(event.getCuentaOrigen())) {
                checkImportesRedondos(event, nowMs, anomalies);
            }

            // ANO-007 — gran transferencia antes de cierre de cuenta
            if (!isBlank(event.getCuentaOrigen())) {
                checkCierreConTransferencia(event, nowMs, anomalies);
            }

            // ANO-026 — cajeros geograficamente imposibles
            if ("RETIRO_CAJERO".equalsIgnoreCase(event.getTipoOperacion())
                    && !isBlank(event.getCuentaOrigen())) {
                checkCajerosImposibles(event, nowMs, anomalies);
            }

            // ANO-027 — maximo diario en cajero 7 dias consecutivos
            if ("RETIRO_CAJERO".equalsIgnoreCase(event.getTipoOperacion())
                    && event.getImporte() != null
                    && !isBlank(event.getCuentaOrigen())) {
                checkMaximoDiarioCajero(event, nowMs, anomalies);
            }

            // ANO-028 — pagos simultaneos en fisicos distintos
            if ("PAGO_COMERCIO".equalsIgnoreCase(event.getTipoOperacion())
                    && !isBlank(event.getCuentaOrigen())) {
                checkPagosSimultaneosFisicos(event, nowMs, anomalies);
            }

            // ANO-029 — rafaga pagos online
            if ("PAGO_ONLINE".equalsIgnoreCase(event.getTipoOperacion())
                    && !isBlank(event.getCuentaOrigen())) {
                checkRafagaPagosOnline(event, nowMs, anomalies);
            }

            // ANO-036 — gap temporal inexplicable
            if (!isBlank(event.getCuentaOrigen())) {
                checkGapTemporal(event, nowMs, anomalies);
            }

            // ANO-037 — velocidad inhumana entre eventos (bot)
            if (!isBlank(event.getSesionId())) {
                checkBotSpeed(event, nowMs, anomalies);
            }

            // ANO-042 — primera transferencia a destino nuevo por importe alto
            if ("TRANSFERENCIA".equalsIgnoreCase(event.getTipoOperacion())
                    && event.getImporte() != null
                    && event.getImporte() > NEW_DEST_MIN_IMPORTE
                    && !isBlank(event.getCuentaOrigen())
                    && !isBlank(event.getCuentaDestino())) {
                checkNuevoDestinoAlto(event, anomalies);
            }
        }

        return anomalies;
    }

    // ------------------------------------------------------------------
    // Individual check methods
    // ------------------------------------------------------------------

    /** ANO-025: >10 tx de la misma cuenta en 5 minutos */
    private void checkVelocidad(BankingTransactionEvent e, long nowMs, List<String> out) {
        List<String> times = cuentaTimestamps.computeIfAbsent(
                e.getCuentaOrigen(), k -> Collections.synchronizedList(new ArrayList<>()));
        times.add(e.getTimestamp());
        long cutoff = nowMs - VELOCITY_WINDOW_MS;
        times.removeIf(ts -> { long t = parseTimestampMillis(ts); return t > 0 && t < cutoff; });
        if (times.size() > VELOCITY_THRESHOLD) {
            out.add(ano("ANO-025", "ALTA", "Velocidad",
                    "Cuenta " + safe(e.getCuentaOrigen()) + " supera " + VELOCITY_THRESHOLD
                            + " transacciones en 5 minutos (" + times.size() + " detectadas)"));
        }
    }

    /** ANO-002: >=5 destinos distintos en 24h desde la misma cuenta */
    private void checkSmurfingMultipleDestinos(BankingTransactionEvent e, long nowMs, List<String> out) {
        String dayKey = toDateKey(nowMs);
        Map<String, Set<String>> byDay = smurfingDestByDay
                .computeIfAbsent(e.getCuentaOrigen(), k -> new ConcurrentHashMap<>());
        Set<String> dests = byDay.computeIfAbsent(dayKey, k -> ConcurrentHashMap.newKeySet());
        dests.add(e.getCuentaDestino());
        if (dests.size() >= SMURFING_DEST_THRESHOLD) {
            out.add(ano("ANO-002", "ALTA", "Blanqueo",
                    "Smurfing multiples destinos: cuenta " + safe(e.getCuentaOrigen())
                            + " envio a " + dests.size()
                            + " destinos distintos en 24h (umbral: " + SMURFING_DEST_THRESHOLD + ")"));
        }
    }

    /** ANO-003: >=5 origenes distintos hacia el mismo destino en 6h */
    private void checkCuentaMulaReceptora(BankingTransactionEvent e, long nowMs, List<String> out) {
        Deque<TxEntry> hist = mulaIncomingHistory
                .computeIfAbsent(e.getCuentaDestino(), k -> new ArrayDeque<>());
        synchronized (hist) {
            hist.addLast(new TxEntry(nowMs, e.getCuentaOrigen(),
                    e.getImporte() != null ? e.getImporte() : 0));
            long cutoff = nowMs - MULA_WINDOW_MS;
            while (!hist.isEmpty() && hist.peekFirst().millis < cutoff) hist.pollFirst();
            Set<String> uniqueOrigenes = new HashSet<>();
            for (TxEntry t : hist) uniqueOrigenes.add(t.tag);
            if (uniqueOrigenes.size() >= MULA_ORIGEN_THRESHOLD) {
                out.add(ano("ANO-003", "ALTA", "Cuenta mula",
                        "Cuenta mula receptora: " + safe(e.getCuentaDestino())
                                + " recibio de " + uniqueOrigenes.size()
                                + " origenes distintos en 6h (umbral: " + MULA_ORIGEN_THRESHOLD + ")"));
            }
        }
    }

    /** ANO-004: cuenta recibe >10.000 EUR y lo reenvía en <60 min */
    private void checkTransitoRapido(BankingTransactionEvent e, long nowMs, List<String> out) {
        // Register this account as a recipient if it appears as cuenta_destino
        if (!isBlank(e.getCuentaDestino()) && e.getImporte() != null
                && e.getImporte() > TRANSITO_MIN_IMPORTE
                && "TRANSFERENCIA".equalsIgnoreCase(e.getTipoOperacion())) {
            Deque<TxEntry> recv = cuentaRecepciones
                    .computeIfAbsent(e.getCuentaDestino(), k -> new ArrayDeque<>());
            synchronized (recv) {
                recv.addLast(new TxEntry(nowMs, e.getCuentaOrigen(), e.getImporte()));
            }
        }
        // Check if this account (as origen) recently received a similar amount
        if (!isBlank(e.getCuentaOrigen()) && e.getImporte() != null
                && e.getImporte() > TRANSITO_MIN_IMPORTE
                && "TRANSFERENCIA".equalsIgnoreCase(e.getTipoOperacion())) {
            Deque<TxEntry> recv = cuentaRecepciones.get(e.getCuentaOrigen());
            if (recv != null) {
                long cutoff = nowMs - TRANSITO_WINDOW_MS;
                synchronized (recv) {
                    for (TxEntry r : recv) {
                        if (r.millis >= cutoff
                                && Math.abs(r.importe - e.getImporte()) / r.importe <= TRANSITO_TOLERANCE) {
                            out.add(ano("ANO-004", "ALTA", "Blanqueo",
                                    "Transito rapido: cuenta " + safe(e.getCuentaOrigen())
                                            + " recibio y reenvio " + e.getImporte()
                                            + " EUR en menos de 60 minutos"));
                            break;
                        }
                    }
                }
            }
        }
    }

    /** ANO-006: mismo importe redondo (multiplo de 1000) >=3 veces en 7 dias */
    private void checkImportesRedondos(BankingTransactionEvent e, long nowMs, List<String> out) {
        double imp = e.getImporte();
        if (imp % 1_000.0 != 0.0) return;
        String key = e.getCuentaOrigen() + "@" + (long) imp;
        List<Long> hits = importesRedondosHits
                .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        hits.add(nowMs);
        long cutoff = nowMs - ROUND_WINDOW_MS;
        hits.removeIf(t -> t < cutoff);
        if (hits.size() >= ROUND_THRESHOLD) {
            out.add(ano("ANO-006", "MEDIA", "Blanqueo",
                    "Importes redondos repetidos: cuenta " + safe(e.getCuentaOrigen())
                            + " realizo " + hits.size() + " transferencias de exactamente "
                            + (long) imp + " EUR en 7 dias"));
        }
    }

    /** ANO-007: TRANSFERENCIA >10.000 seguida de CIERRE_CUENTA en <48h */
    private void checkCierreConTransferencia(BankingTransactionEvent e, long nowMs, List<String> out) {
        if ("TRANSFERENCIA".equalsIgnoreCase(e.getTipoOperacion())
                && e.getImporte() != null && e.getImporte() > CIERRE_MIN_TRANSFERENCIA) {
            Deque<TxEntry> hist = grandesTransferencias
                    .computeIfAbsent(e.getCuentaOrigen(), k -> new ArrayDeque<>());
            synchronized (hist) {
                hist.addLast(new TxEntry(nowMs, "TRANSFERENCIA", e.getImporte()));
            }
        }
        if ("CIERRE_CUENTA".equalsIgnoreCase(e.getTipoOperacion())) {
            Deque<TxEntry> hist = grandesTransferencias.get(e.getCuentaOrigen());
            if (hist != null) {
                long cutoff = nowMs - CIERRE_WINDOW_MS;
                synchronized (hist) {
                    for (TxEntry t : hist) {
                        if (t.millis >= cutoff) {
                            out.add(ano("ANO-007", "ALTA", "Blanqueo",
                                    "Vaciado previo al cierre: TRANSFERENCIA de " + t.importe
                                            + " EUR seguida de CIERRE_CUENTA en <48h en cuenta "
                                            + safe(e.getCuentaOrigen())));
                            break;
                        }
                    }
                }
            }
        }
    }

    /** ANO-026: RETIRO_CAJERO en ciudades distintas con <90 min de diferencia */
    private void checkCajerosImposibles(BankingTransactionEvent e, long nowMs, List<String> out) {
        String city = extractCityFromDevice(e.getDispositivoId());
        if (city == null) return;
        Deque<TxEntry> hist = cajeroHistory
                .computeIfAbsent(e.getCuentaOrigen(), k -> new ArrayDeque<>());
        synchronized (hist) {
            long cutoff = nowMs - ATM_TRAVEL_MIN_MS;
            for (TxEntry prev : hist) {
                if (prev.millis >= cutoff && !city.equalsIgnoreCase(prev.tag)) {
                    long diffMin = (nowMs - prev.millis) / 60_000L;
                    out.add(ano("ANO-026", "CRITICA", "Velocidad",
                            "Retiro en cajero imposible: cuenta " + safe(e.getCuentaOrigen())
                                    + " en " + prev.tag + " y luego en " + city
                                    + " con solo " + diffMin + " minutos de diferencia"));
                    break;
                }
            }
            hist.addLast(new TxEntry(nowMs, city, e.getImporte() != null ? e.getImporte() : 0));
            while (!hist.isEmpty() && hist.peekFirst().millis < nowMs - ATM_TRAVEL_MIN_MS * 2)
                hist.pollFirst();
        }
    }

    /** ANO-027: mismo importe exacto en RETIRO_CAJERO durante 7 dias consecutivos */
    private void checkMaximoDiarioCajero(BankingTransactionEvent e, long nowMs, List<String> out) {
        String today = toDateKey(nowMs);
        String todayKey = e.getCuentaOrigen() + "@" + today;
        List<Double> todayList = cajeroImportesPorDia
                .computeIfAbsent(todayKey, k -> Collections.synchronizedList(new ArrayList<>()));
        todayList.add(e.getImporte());

        // Check if same exact amount also appeared in the previous 6 days
        int consecutiveDays = 0;
        for (int d = 1; d <= 6; d++) {
            String prevKey = e.getCuentaOrigen() + "@"
                    + toDateKey(nowMs - (long) d * 24L * 60L * 60_000L);
            List<Double> prev = cajeroImportesPorDia.get(prevKey);
            if (prev != null && prev.contains(e.getImporte())) {
                consecutiveDays++;
            } else {
                break;
            }
        }
        if (consecutiveDays >= 6) {
            out.add(ano("ANO-027", "MEDIA", "Velocidad",
                    "Retiro maximo diario en cajero: cuenta " + safe(e.getCuentaOrigen())
                            + " retiro exactamente " + e.getImporte()
                            + " EUR en 7 dias consecutivos (posible vaciado planificado)"));
        }
    }

    /** ANO-028: dos PAGO_COMERCIO en terminales fisicos distintos en <2 min */
    private void checkPagosSimultaneosFisicos(BankingTransactionEvent e, long nowMs, List<String> out) {
        String terminal = e.getDispositivoId();
        if (isBlank(terminal)) return;
        Deque<TxEntry> hist = fisicoPagoHistory
                .computeIfAbsent(e.getCuentaOrigen(), k -> new ArrayDeque<>());
        synchronized (hist) {
            long cutoff = nowMs - FISICO_WINDOW_MS;
            for (TxEntry prev : hist) {
                if (prev.millis >= cutoff && !terminal.equals(prev.tag)) {
                    out.add(ano("ANO-028", "CRITICA", "Velocidad",
                            "Pago fisico simultaneo imposible: cuenta " + safe(e.getCuentaOrigen())
                                    + " en terminal " + prev.tag + " y en " + terminal
                                    + " con menos de 2 minutos de diferencia (tarjeta clonada)"));
                    break;
                }
            }
            hist.addLast(new TxEntry(nowMs, terminal, 0));
            while (!hist.isEmpty() && hist.peekFirst().millis < cutoff) hist.pollFirst();
        }
    }

    /** ANO-029: >=5 PAGO_ONLINE desde la misma cuenta en <2 min */
    private void checkRafagaPagosOnline(BankingTransactionEvent e, long nowMs, List<String> out) {
        Deque<Long> times = pagoOnlineTimestamps
                .computeIfAbsent(e.getCuentaOrigen(), k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(nowMs);
            long cutoff = nowMs - PAGO_ONLINE_WINDOW_MS;
            while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
            if (times.size() >= PAGO_ONLINE_THRESHOLD) {
                out.add(ano("ANO-029", "ALTA", "Velocidad",
                        "Rafaga de pagos online: cuenta " + safe(e.getCuentaOrigen())
                                + " realizo " + times.size()
                                + " PAGO_ONLINE en menos de 2 minutos (posible bot probando tarjeta)"));
            }
        }
    }

    /** ANO-036: gap de mas de 6h entre eventos consecutivos de la misma cuenta */
    private void checkGapTemporal(BankingTransactionEvent e, long nowMs, List<String> out) {
        Long prev = ultimoTimestampPorCuenta.put(e.getCuentaOrigen(), nowMs);
        if (prev != null && prev > 0) {
            long gapMs = nowMs - prev;
            if (gapMs > GAP_THRESHOLD_MS) {
                long horas = gapMs / 3_600_000L;
                long mins  = (gapMs % 3_600_000L) / 60_000L;
                out.add(ano("ANO-036", "ALTA", "Integridad logs",
                        "Gap temporal de " + horas + "h " + mins + "min en cuenta "
                                + safe(e.getCuentaOrigen())
                                + " — posible borrado o manipulacion de registros"));
            }
        }
    }

    /** ANO-037: >=3 eventos en la misma sesion con <500ms entre ellos (bot) */
    private void checkBotSpeed(BankingTransactionEvent e, long nowMs, List<String> out) {
        Deque<Long> times = sesionEventos
                .computeIfAbsent(e.getSesionId(), k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(nowMs);
            if (times.size() > 20) times.pollFirst(); // keep last 20
            if (times.size() >= BOT_SPEED_COUNT) {
                Long[] arr = times.toArray(new Long[0]);
                int consecutive = 1;
                for (int i = arr.length - 1; i > 0; i--) {
                    if (arr[i] - arr[i - 1] < BOT_SPEED_MS) {
                        consecutive++;
                        if (consecutive >= BOT_SPEED_COUNT) {
                            out.add(ano("ANO-037", "ALTA", "Integridad logs",
                                    "Velocidad inhumana: sesion " + e.getSesionId()
                                            + " genero " + consecutive
                                            + " eventos en menos de 500ms (posible bot/script)"));
                            break;
                        }
                    } else {
                        consecutive = 1;
                    }
                }
            }
        }
    }

    /** ANO-042: primera TRANSFERENCIA a un destino nunca visto con importe >3.000 */
    private void checkNuevoDestinoAlto(BankingTransactionEvent e, List<String> out) {
        Set<String> known = destinosConocidos
                .computeIfAbsent(e.getCuentaOrigen(), k -> ConcurrentHashMap.newKeySet());
        boolean isNew = known.add(e.getCuentaDestino());
        // Only alert if the account has a transaction history (at least one previous destination)
        if (isNew && known.size() > 1) {
            out.add(ano("ANO-042", "ALTA", "Fraude externo",
                    "Primera transferencia a destino nuevo " + safe(e.getCuentaDestino())
                            + " por importe alto " + e.getImporte()
                            + " EUR desde cuenta " + safe(e.getCuentaOrigen())
                            + " (posible estafa del CEO o fraude de urgencia)"));
        }
    }

    // ------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------

    private String ano(String id, String severidad, String categoria, String detalle) {
        return id + "|" + severidad + "|" + categoria + "|" + detalle;
    }

    private String safe(String v) {
        return (v == null || v.trim().isEmpty()) ? "N/A" : v;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Extracts city name from device ID in format ATM-CityName-NNN */
    private String extractCityFromDevice(String deviceId) {
        if (isBlank(deviceId)) return null;
        String[] parts = deviceId.split("-");
        if (parts.length >= 2 && "ATM".equalsIgnoreCase(parts[0])) {
            return parts[1];
        }
        return null;
    }

    /** Returns a date key "yyyy-MM-dd" for a given epoch-millis value */
    private String toDateKey(long millis) {
        return java.time.Instant.ofEpochMilli(millis)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .toString();
    }

    /**
     * Parses a timestamp string (ISO-8601, epoch-millis, or yyyy-MM-dd HH:mm:ss) to epoch millis.
     * Returns 0 if parsing fails.
     */
    private long parseTimestampMillis(String ts) {
        if (isBlank(ts)) return 0L;
        try {
            return Long.parseLong(ts.trim());
        } catch (NumberFormatException ignored) { /* not epoch millis */ }
        try {
            return java.time.Instant.parse(ts.trim()).toEpochMilli();
        } catch (Exception ignored) { /* not ISO-8601 with Z */ }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(ts.trim(), fmt)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) { /* not this format either */ }
        try {
            // ISO-8601 with milliseconds: 2026-05-23T14:32:01.123
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
            return LocalDateTime.parse(ts.trim(), fmt)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) { /* give up */ }
        return 0L;
    }
}
