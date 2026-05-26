package com.example.auditpanel.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "auditpanel.sql.use-pg-notify", havingValue = "false", matchIfMissing = true)
public class PollingAnomalyPollerService {

    private final AnomalyBroadcastService broadcastService;
    private final long intervalMs;

    public PollingAnomalyPollerService(AnomalyBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
        this.intervalMs = 3000L; // default; can be made configurable later
    }

    @Scheduled(fixedDelayString = "${auditpanel.broadcast.poll-interval-ms:3000}")
    public void poll() {
        try {
            var newItems = broadcastService.fetchSinceLast();
            for (var a : newItems) {
                broadcastService.notifyNewAnomaly(a);
            }
        } catch (Exception e) {
            // ignore and continue
        }
    }
}
