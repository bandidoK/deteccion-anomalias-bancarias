package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class AnomalyDetectorService {
    private final Deque<EventSnapshot> snapshots = new ConcurrentLinkedDeque<>();
    private final List<String> globalAlerts = Collections.synchronizedList(new ArrayList<>());
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SNAPSHOTS = 1000;
    private static final int MAX_ALERTS = 200;
    private static final String[] SUSPICIOUS_KEYWORDS = {"timeout", "exception", "failed", "unauthorized", "denied", "error"};

    public List<String> detect(LogEvent event) {
        Instant now = Instant.now();
        EventSnapshot snapshot = new EventSnapshot(now, event.getSource(), event.getLevel(), event.getMessage());
        snapshots.addLast(snapshot);
        cleanup(now);

        long totalCount = snapshots.size();
        long errorCount = snapshots.stream().filter(s -> "ERROR".equalsIgnoreCase(s.level)).count();
        double errorRate = totalCount == 0 ? 0 : ((double) errorCount / totalCount) * 100;

        List<String> anomalies = new ArrayList<>();

        if (errorRate > 25) {
            String alert = String.format("Alta tasa de errores: %.1f%% en los últimos %d minutos", errorRate, WINDOW.toMinutes());
            recordAlert(alert, anomalies);
        }

        Map<String, Long> repeatedMessages = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.message == null ? "" : s.message, Collectors.counting()));

        repeatedMessages.entrySet().stream()
                .filter(entry -> !entry.getKey().isEmpty() && entry.getValue() > 4)
                .findFirst()
                .ifPresent(entry -> recordAlert("Mensaje repetido: '" + entry.getKey() + "' aparece " + entry.getValue() + " veces", anomalies));

        if (event.getMessage() != null) {
            String lowerMessage = event.getMessage().toLowerCase();
            for (String keyword : SUSPICIOUS_KEYWORDS) {
                if (lowerMessage.contains(keyword)) {
                    recordAlert("Palabra sospechosa detectada: '" + keyword + "' en mensaje", anomalies);
                    break;
                }
            }
        }

        long recentSourceErrors = snapshots.stream()
                .filter(s -> event.getSource() != null && event.getSource().equalsIgnoreCase(s.source))
                .filter(s -> "ERROR".equalsIgnoreCase(s.level))
                .count();

        if (recentSourceErrors >= 5 && event.getSource() != null) {
            recordAlert("Fuente " + event.getSource() + " registra " + recentSourceErrors + " errores en la ventana reciente", anomalies);
        }

        var errorsBySource = snapshots.stream()
                .filter(s -> "ERROR".equalsIgnoreCase(s.level))
                .collect(java.util.stream.Collectors.groupingBy(s -> s.source == null ? "unknown" : s.source, java.util.stream.Collectors.counting()));
        double averageSourceErrors = errorsBySource.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        if (recentSourceErrors > averageSourceErrors * 3 && recentSourceErrors > 2 && event.getSource() != null) {
            recordAlert("Spike inusual en la fuente " + event.getSource() + ": " + recentSourceErrors + " errores frente a un promedio de " + String.format("%.1f", averageSourceErrors), anomalies);
        }

        if (event.getLevel() != null && event.getLevel().equalsIgnoreCase("ERROR") && anomalies.isEmpty()) {
            recordAlert("Evento ERROR registrado; revisar logs recientes.", anomalies);
        }

        return anomalies;
    }

    public List<String> getGlobalAnomalies() {
        return new ArrayList<>(globalAlerts);
    }

    private void recordAlert(String alert, List<String> anomalies) {
        if (!globalAlerts.contains(alert)) {
            globalAlerts.add(alert);
        }
        anomalies.add(alert);
        if (globalAlerts.size() > MAX_ALERTS) {
            while (globalAlerts.size() > MAX_ALERTS) {
                globalAlerts.remove(0);
            }
        }
    }

    private void cleanup(Instant now) {
        while (!snapshots.isEmpty()) {
            EventSnapshot head = snapshots.peekFirst();
            if (head == null) {
                break;
            }
            if (Duration.between(head.timestamp, now).compareTo(WINDOW) > 0) {
                snapshots.removeFirst();
            } else {
                break;
            }
        }
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeFirst();
        }
    }

    private static class EventSnapshot {
        private final Instant timestamp;
        private final String source;
        private final String level;
        private final String message;

        public EventSnapshot(Instant timestamp, String source, String level, String message) {
            this.timestamp = timestamp;
            this.source = source;
            this.level = level;
            this.message = message;
        }
    }
}
