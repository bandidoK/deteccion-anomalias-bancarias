package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(LogPublisherService.class)
public class NoOpLogPublisherService implements LogPublisherService {
    @Override
    public void publish(LogEvent event) {
        // No-op publisher cuando no hay sink externo habilitado.
    }
}
