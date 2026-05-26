package com.example.auditpanel.service;

import com.example.auditpanel.model.AlertaAuditoriaEntity;
import com.example.auditpanel.model.AnomalyEntity;
import com.example.auditpanel.repository.AlertaAuditoriaRepository;
import com.example.auditpanel.repository.AnomalyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "auditpanel.sql.use-pg-notify", havingValue = "true")
public class PostgresNotifyListenerService {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyListenerService.class);

    private final DataSource dataSource;
    private final AnomalyBroadcastService broadcastService;

    private final ObjectMapper mapper;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;
    @Value("${auditpanel.sql.pg-notify-channel:anomalies_channel}")
    private String channel;
    private final AnomalyRepository anomalyRepository;
    private final AlertaAuditoriaRepository alertaRepository;

    public PostgresNotifyListenerService(DataSource dataSource, AnomalyBroadcastService broadcastService, ObjectMapper mapper,
                                         AnomalyRepository anomalyRepository, AlertaAuditoriaRepository alertaRepository) {
        this.dataSource = dataSource;
        this.broadcastService = broadcastService;
        this.mapper = mapper;
        this.anomalyRepository = anomalyRepository;
        this.alertaRepository = alertaRepository;
    }

    @PostConstruct
    public void start() {
        log.info("Iniciando listener de PostgreSQL para el canal '{}'", channel);
        exec.execute(this::listenLoop);
    }

    @PreDestroy
    public void stop() {
        log.info("Deteniendo listener de PostgreSQL para el canal '{}'", channel);
        running = false;
        exec.shutdownNow();
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(true);
                log.info("Conectado a PostgreSQL. Suscribiendo al canal '{}'", channel);
                PGConnection pgconn = conn.unwrap(PGConnection.class);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN " + channel);
                }
                log.info("Suscripción activa en el canal '{}'", channel);
                while (running) {
                    try {
                        PGNotification[] notifications = pgconn.getNotifications();
                        if (notifications != null && notifications.length > 0) {
                            log.info("Recibidas {} notificaciones en el canal '{}'", notifications.length, channel);
                            for (PGNotification n : notifications) {
                                log.info("Notificación recibida en canal '{}' payload='{}'", channel, n.getParameter());
                                handleNotification(n.getParameter());
                            }
                        }
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo conectar o suscribirse al canal '{}' de PostgreSQL. Reintentando en 5 segundos.", channel, e);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleNotification(String payload) {
        if (payload == null) {
            log.debug("Se recibió una notificación nula en el canal '{}'", channel);
            return;
        }

        try {
            long id = Long.parseLong(payload);
            log.debug("Intentando resolver alerta por id '{}' desde notificación de PostgreSQL", id);
            var alertaOpt = alertaRepository.findById(id);
            if (alertaOpt.isPresent()) {
                log.info("Notificación PostgreSQL resuelta como alerta persistida id={}", id);
                broadcastService.notifyNewAnomaly(toAnomalyEntity(alertaOpt.get()));
                return;
            }

            var anomalieOpt = anomalyRepository.findById(id);
            if (anomalieOpt.isPresent()) {
                log.info("Notificación PostgreSQL resuelta como anomalía persistida id={}", id);
                broadcastService.notifyNewAnomaly(anomalieOpt.get());
                return;
            }

            log.warn("La notificación PostgreSQL con id={} no existe en las tablas de alertas ni anomalías", id);
            return;
        } catch (NumberFormatException ignored) {
            // payload no es un id numérico, intentamos tratarlo como JSON
        }

        try {
            AlertaAuditoriaEntity alerta = mapper.readValue(payload, AlertaAuditoriaEntity.class);
            if (alerta.getId() != null) {
                var alertaOpt = alertaRepository.findById(alerta.getId());
                alertaOpt.ifPresentOrElse(
                        value -> {
                            log.info("Notificación PostgreSQL JSON resuelta como alerta persistida id={}", value.getId());
                            broadcastService.notifyNewAnomaly(toAnomalyEntity(value));
                        },
                        () -> {
                            log.info("Notificación PostgreSQL JSON emitida como alerta en vuelo tipo='{}'", alerta.getTipoAlerta());
                            broadcastService.notifyNewAnomaly(toAnomalyEntity(alerta));
                        }
                );
                return;
            }

            log.info("Notificación PostgreSQL JSON emitida como alerta en vuelo tipo='{}'", alerta.getTipoAlerta());
            broadcastService.notifyNewAnomaly(toAnomalyEntity(alerta));
            return;
        } catch (IOException ignored) {
            // No se pudo parsear como alerta, probamos como anomalía legacy
        }

        try {
            AnomalyEntity a = mapper.readValue(payload, AnomalyEntity.class);
            log.debug("Notificación PostgreSQL interpretada como JSON legacy de anomalía tipo='{}'", a.getTipoAnomalia());
            if (a.getId() != null) {
                var opt = anomalyRepository.findById(a.getId());
                opt.ifPresentOrElse(
                        b -> {
                            log.info("Notificación PostgreSQL JSON legacy resuelta como anomalía persistida id={}", b.getId());
                            broadcastService.notifyNewAnomaly(b);
                        },
                        () -> log.warn("La notificación PostgreSQL JSON legacy referenciaba id={} pero no existe en la tabla", a.getId())
                );
            } else {
                log.info("Notificación PostgreSQL JSON legacy emitida como anomalía en vuelo tipo='{}'", a.getTipoAnomalia());
                broadcastService.notifyNewAnomaly(a);
            }
        } catch (IOException e) {
            log.warn("No se pudo parsear la notificación PostgreSQL como id numérico, alerta JSON ni anomalía JSON. payload='{}'", payload, e);
        }
    }

    private AnomalyEntity toAnomalyEntity(AlertaAuditoriaEntity alerta) {
        if (alerta == null) {
            return null;
        }

        AnomalyEntity anomaly = new AnomalyEntity();
        anomaly.setId(alerta.getId());
        anomaly.setTimestampDetection(alerta.getTimestampDetection());
        anomaly.setTipoAnomalia(alerta.getTipoAlerta() != null ? alerta.getTipoAlerta() : "fraude");
        anomaly.setSeveridad(alerta.getSeveridad() != null ? alerta.getSeveridad() : "ALTA");
        anomaly.setEntidadSospechosa(alerta.getEntidadSospechosa());
        anomaly.setDescripcion(alerta.getDescripcion());
        anomaly.setCreatedAt(alerta.getCreatedAt());
        return anomaly;
    }
}
