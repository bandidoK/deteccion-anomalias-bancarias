package com.example.auditpanel.service;

import com.example.auditpanel.model.AlertaAuditoriaEntity;
import com.example.auditpanel.model.AnomalyEntity;
import com.example.auditpanel.repository.AlertaAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "auditpanel.sql.enabled", havingValue = "true")
public class AnomalyBroadcastService {

    private final AlertaAuditoriaRepository alertaRepository;
    private final ObjectMapper mapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong lastSeenId = new AtomicLong(0);

    public AnomalyBroadcastService(AlertaAuditoriaRepository alertaRepository, ObjectMapper mapper) {
        this.alertaRepository = alertaRepository;
        this.mapper = mapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    public List<AnomalyEntity> listAll() {
        return alertaRepository.findAll().stream()
                .map(this::toAnomalyEntity)
                .collect(Collectors.toList());
    }

    public void notifyNewAnomaly(AnomalyEntity a) {
        if (a == null) return;
        lastSeenId.set(Math.max(lastSeenId.get(), a.getId()));
        broadcast(a);
    }

    public List<AnomalyEntity> fetchSinceLast() {
        Long last = lastSeenId.get();
        List<AlertaAuditoriaEntity> newOnes = alertaRepository.findByIdGreaterThanOrderByIdAsc(last);
        if (!newOnes.isEmpty()) {
            for (AlertaAuditoriaEntity alerta : newOnes) {
                lastSeenId.set(Math.max(lastSeenId.get(), alerta.getId()));
            }
        }
        return newOnes.stream()
                .map(this::toAnomalyEntity)
                .collect(Collectors.toList());
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

    private void broadcast(AnomalyEntity anomaly) {
        String payload;
        try {
            payload = mapper.writeValueAsString(anomaly);
        } catch (IOException e) {
            payload = "{}";
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("anomaly").data(payload));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
