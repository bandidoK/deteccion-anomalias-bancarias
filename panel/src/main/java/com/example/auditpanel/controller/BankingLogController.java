package com.example.auditpanel.controller;

import com.example.auditpanel.model.AnomalyEntity;
import com.example.auditpanel.model.BankingTransactionEvent;
import com.example.auditpanel.repository.AnomalyRepository;
import com.example.auditpanel.service.BankingAnomalyDetectorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for banking transaction log ingestion and anomaly detection.
 *
 * Endpoints:
 *   POST /api/banking/transactions  - single transaction
 *   POST /api/banking/batch         - list of transactions
 *   POST /api/banking/upload-csv    - pipe-separated CSV file
 *
 * CSV format (pipe-separated, with header):
 *   transaction_id|timestamp|cuenta_origen|cuenta_destino|importe|divisa|
 *   tipo_operacion|ip_origen|dispositivo_id|pais_origen|pais_destino|
 *   sesion_id|concepto|estado
 */
@RestController
@RequestMapping("/api/banking")
public class BankingLogController {

    private final BankingAnomalyDetectorService detectorService;
    private final AnomalyRepository anomalyRepository;

    // AnomalyRepository may not be available when sql is disabled; use Optional injection pattern
    public BankingLogController(BankingAnomalyDetectorService detectorService,
                                 org.springframework.beans.factory.ObjectProvider<AnomalyRepository> anomalyRepositoryProvider) {
        this.detectorService = detectorService;
        this.anomalyRepository = anomalyRepositoryProvider.getIfAvailable();
    }

    // ------------------------------------------------------------------
    // POST /api/banking/transactions
    // ------------------------------------------------------------------

    /**
     * Accept a single BankingTransactionEvent as JSON, run anomaly detection,
     * persist any anomalies found, and return a summary response.
     */
    @PostMapping("/transactions")
    public ResponseEntity<Map<String, Object>> processSingleTransaction(
            @RequestBody BankingTransactionEvent event) {

        List<String> anomalies = new ArrayList<>();
        try {
            anomalies = detectorService.detectBanking(event);
            persistAnomalies(anomalies, event.getCuentaOrigen());
        } catch (Exception e) {
            // log and continue — return partial result
            anomalies.add("ERROR|INTERNA|Sistema|" + e.getMessage());
        }

        return ResponseEntity.ok(buildResponse(1, anomalies.size(), anomalies));
    }

    // ------------------------------------------------------------------
    // POST /api/banking/batch
    // ------------------------------------------------------------------

    /**
     * Accept a JSON array of BankingTransactionEvent objects, process each one,
     * and return a combined summary.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> processBatch(
            @RequestBody List<BankingTransactionEvent> events) {

        List<String> allAnomalies = new ArrayList<>();
        int processed = 0;

        if (events != null) {
            for (BankingTransactionEvent event : events) {
                try {
                    List<String> found = detectorService.detectBanking(event);
                    persistAnomalies(found, event.getCuentaOrigen());
                    allAnomalies.addAll(found);
                    processed++;
                } catch (Exception e) {
                    allAnomalies.add("ERROR|INTERNA|Sistema|" + e.getMessage());
                    processed++;
                }
            }
        }

        return ResponseEntity.ok(buildResponse(processed, allAnomalies.size(), allAnomalies));
    }

    // ------------------------------------------------------------------
    // POST /api/banking/upload-csv
    // ------------------------------------------------------------------

    /**
     * Accept a pipe-separated CSV file, parse it line by line (skipping the header),
     * run anomaly detection on each transaction, persist anomalies, and return a summary.
     */
    @PostMapping("/upload-csv")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("file") MultipartFile file) {

        List<String> allAnomalies = new ArrayList<>();
        int processed = 0;
        List<String> parseErrors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "No se recibio ningun archivo o el archivo esta vacio.");
            return ResponseEntity.badRequest().body(err);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip blank lines
                if (line.trim().isEmpty()) continue;

                // Skip header line
                if (firstLine) {
                    firstLine = false;
                    // If the line starts with a known header token, skip it
                    if (line.toLowerCase().startsWith("transaction_id")) continue;
                }

                try {
                    BankingTransactionEvent event = parseCsvLine(line);
                    List<String> found = detectorService.detectBanking(event);
                    persistAnomalies(found, event.getCuentaOrigen());
                    allAnomalies.addAll(found);
                    processed++;
                } catch (Exception e) {
                    parseErrors.add("Linea ignorada por error de parseo: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Error leyendo el archivo: " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }

        Map<String, Object> response = buildResponse(processed, allAnomalies.size(), allAnomalies);
        if (!parseErrors.isEmpty()) {
            response.put("parseErrors", parseErrors);
        }
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Parse a single pipe-separated line into a BankingTransactionEvent.
     * Expected columns (14 total):
     *   0  transaction_id
     *   1  timestamp
     *   2  cuenta_origen
     *   3  cuenta_destino
     *   4  importe
     *   5  divisa
     *   6  tipo_operacion
     *   7  ip_origen
     *   8  dispositivo_id
     *   9  pais_origen
     *   10 pais_destino
     *   11 sesion_id
     *   12 concepto
     *   13 estado
     */
    private BankingTransactionEvent parseCsvLine(String line) {
        // Limit=-1 keeps trailing empty tokens
        String[] parts = line.split("\\|", -1);

        BankingTransactionEvent event = new BankingTransactionEvent();
        event.setTransactionId(get(parts, 0));
        event.setTimestamp(get(parts, 1));
        event.setCuentaOrigen(get(parts, 2));
        event.setCuentaDestino(get(parts, 3));

        String importeStr = get(parts, 4);
        if (importeStr != null && !importeStr.isEmpty()) {
            try {
                event.setImporte(Double.parseDouble(importeStr));
            } catch (NumberFormatException e) {
                event.setImporte(null);
            }
        }

        event.setDivisa(get(parts, 5));
        event.setTipoOperacion(get(parts, 6));
        event.setIpOrigen(get(parts, 7));
        event.setDispositivoId(get(parts, 8));
        event.setPaisOrigen(get(parts, 9));
        event.setPaisDestino(get(parts, 10));
        event.setSesionId(get(parts, 11));
        event.setConcepto(get(parts, 12));
        event.setEstado(get(parts, 13));

        return event;
    }

    /** Safe array accessor — returns null when index is out of bounds or value is blank. */
    private String get(String[] parts, int index) {
        if (index >= parts.length) return null;
        String val = parts[index].trim();
        return val.isEmpty() ? null : val;
    }

    /**
     * Persist detected anomalies to the AnomalyEntity table if the repository is available.
     * Parses the "ANO-XXX|SEVERIDAD|CATEGORIA|detalle" format.
     */
    private void persistAnomalies(List<String> anomalies, String cuentaOrigen) {
        if (anomalyRepository == null || anomalies == null || anomalies.isEmpty()) return;
        try {
            for (String anomalyStr : anomalies) {
                String[] parts = anomalyStr.split("\\|", 4);
                AnomalyEntity entity = new AnomalyEntity();
                entity.setTimestampDetection(Instant.now());
                entity.setCreatedAt(Instant.now());

                if (parts.length >= 1) entity.setAnoId(parts[0]);
                if (parts.length >= 2) entity.setSeveridad(parts[1]);
                if (parts.length >= 3) entity.setCategoria(parts[2]);
                if (parts.length >= 4) entity.setDescripcion(parts[3]);

                // tipoAnomalia combines anoId + category for backward compatibility
                entity.setTipoAnomalia(parts.length >= 1 ? parts[0] : "DESCONOCIDA");
                entity.setCuentaId(cuentaOrigen);
                entity.setEntidadSospechosa(cuentaOrigen);

                anomalyRepository.save(entity);
            }
        } catch (Exception e) {
            // Persistence failure must not break the HTTP response
        }
    }

    /** Build the standard JSON response map. */
    private Map<String, Object> buildResponse(int processed, int anomaliesFound,
                                               List<String> anomalies) {
        Map<String, Object> response = new HashMap<>();
        response.put("processed", processed);
        response.put("anomaliesFound", anomaliesFound);
        response.put("anomalies", anomalies);
        return response;
    }
}
