package com.example.auditpanel.controller;

import com.example.auditpanel.model.LogEvent;
import com.example.auditpanel.service.AnomalyDetectorService;
import com.example.auditpanel.service.LogFileIngestionService;
import com.example.auditpanel.service.LogPublisherService;
import com.example.auditpanel.service.LogSearchService;
import com.example.auditpanel.service.LogStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogStorageService storageService;
    private final AnomalyDetectorService anomalyDetector;
    private final List<LogPublisherService> logPublishers;
    private final LogFileIngestionService fileIngestionService;
    private final Optional<LogSearchService> logSearchService;

    public LogController(LogStorageService storageService,
                         AnomalyDetectorService anomalyDetector,
                         List<LogPublisherService> logPublishers,
                         LogFileIngestionService fileIngestionService,
                         Optional<LogSearchService> logSearchService) {
        this.storageService = storageService;
        this.anomalyDetector = anomalyDetector;
        this.logPublishers = logPublishers;
        this.fileIngestionService = fileIngestionService;
        this.logSearchService = logSearchService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestLog(@RequestBody LogEvent event) {
        storageService.save(event);
        logPublishers.forEach(publisher -> publisher.publish(event));
        List<String> anomalies = anomalyDetector.detect(event);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("anomalies", anomalies);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@RequestBody List<LogEvent> events) {
        storageService.saveAll(events);
        List<String> anomalies = new ArrayList<>();
        events.stream()
                .filter(Objects::nonNull)
                .forEach(event -> {
                    anomalies.addAll(anomalyDetector.detect(event));
                    logPublishers.forEach(publisher -> publisher.publish(event));
                });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("ingested", events.size());
        response.put("anomalies", anomalies);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadLogFile(@RequestParam("file") MultipartFile file) throws IOException {
        List<LogEvent> events = fileIngestionService.parse(file);
        storageService.saveAll(events);
        List<String> anomalies = new ArrayList<>();
        events.stream()
                .filter(Objects::nonNull)
                .forEach(event -> {
                    anomalies.addAll(anomalyDetector.detect(event));
                    logPublishers.forEach(publisher -> publisher.publish(event));
                });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("ingested", events.size());
        response.put("anomalies", anomalies);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<LogEvent>> getRecentEvents(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String level,
            @RequestParam(required = false, defaultValue = "60") int minutes,
            @RequestParam(required = false) String contains,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        Instant since = minutes <= 0 ? null : Instant.now().minusSeconds(minutes * 60L);
        if (logSearchService.isPresent()) {
            return ResponseEntity.ok(logSearchService.get().search(source, level, since, contains, limit));
        }
        return ResponseEntity.ok(storageService.queryEvents(source, level, since, contains, limit));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEvents", storageService.getTotalEvents());
        summary.put("levels", storageService.getLevelCountsSnapshot());
        summary.put("sources", storageService.getSourceCountsSnapshot());
        summary.put("topSources", topEntries(storageService.getSourceCountsSnapshot(), 5));
        summary.put("topLevels", topEntries(storageService.getLevelCountsSnapshot(), 5));
        summary.put("recent", storageService.getRecentEvents().stream().limit(20).collect(Collectors.toList()));
        summary.put("globalAnomalies", anomalyDetector.getGlobalAnomalies());
        summary.put("availableSources", storageService.getAvailableSources());
        summary.put("availableLevels", storageService.getAvailableLevels());
        return ResponseEntity.ok(summary);
    }

    private List<Map<String, Object>> topEntries(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> value = new HashMap<>();
                    value.put("label", entry.getKey());
                    value.put("count", entry.getValue());
                    return value;
                })
                .collect(Collectors.toList());
    }
}
