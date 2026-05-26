package com.example.auditpanel.service;

import com.example.auditpanel.model.AuditLogEntity;
import com.example.auditpanel.model.LogEvent;
import com.example.auditpanel.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(name = "auditpanel.sql.enabled", havingValue = "true")
public class DatabaseLogPublisherService implements LogPublisherService {

    private final AuditLogRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DatabaseLogPublisherService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void publish(LogEvent event) {
        AuditLogEntity e = new AuditLogEntity();
        e.setEventId(event.getId());
        Instant ts = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        e.setEventTimestamp(ts);
        e.setSource(event.getSource());
        e.setLevel(event.getLevel());
        e.setMessage(event.getMessage());
        try {
            String metadata = event.getMetadata() != null ? mapper.writeValueAsString(event.getMetadata()) : null;
            e.setMetadata(metadata);
        } catch (JsonProcessingException ex) {
            e.setMetadata("{}");
        }
        e.setCreatedAt(Instant.now());
        repository.save(e);
    }
}
