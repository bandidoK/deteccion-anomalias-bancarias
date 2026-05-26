package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class LogStorageService {
    private final Deque<LogEvent> recentEvents = new ConcurrentLinkedDeque<>();
    private final ConcurrentMap<String, AtomicLong> levelCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> sourceCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalEvents = new AtomicLong();
    private static final int MAX_RECENT_EVENTS = 1000;

    public void save(LogEvent event) {
        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        recentEvents.addFirst(event);
        if (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }

        levelCounts.computeIfAbsent(event.getLevel(), key -> new AtomicLong()).incrementAndGet();
        sourceCounts.computeIfAbsent(event.getSource(), key -> new AtomicLong()).incrementAndGet();
        totalEvents.incrementAndGet();
    }

    public void saveAll(List<LogEvent> events) {
        events.forEach(this::save);
    }

    public List<LogEvent> getRecentEvents() {
        return Collections.unmodifiableList(new ArrayList<>(recentEvents));
    }

    public List<LogEvent> queryEvents(String source, String level, Instant since, String contains, int limit) {
        return recentEvents.stream()
                .filter(event -> source == null || source.isBlank() || source.equalsIgnoreCase(event.getSource()))
                .filter(event -> level == null || level.isBlank() || level.equalsIgnoreCase(event.getLevel()))
                .filter(event -> since == null || (event.getTimestamp() != null && !event.getTimestamp().isBefore(since)))
                .filter(event -> contains == null || contains.isBlank() || (event.getMessage() != null && event.getMessage().toLowerCase().contains(contains.toLowerCase())))
                .sorted(Comparator.comparing(LogEvent::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public long getTotalEvents() {
        return totalEvents.get();
    }

    public Optional<Long> getLevelCount(String level) {
        return Optional.ofNullable(levelCounts.get(level)).map(AtomicLong::get);
    }

    public Optional<Long> getSourceCount(String source) {
        return Optional.ofNullable(sourceCounts.get(source)).map(AtomicLong::get);
    }

    public Map<String, Long> getLevelCountsSnapshot() {
        return snapshotMap(levelCounts);
    }

    public Map<String, Long> getSourceCountsSnapshot() {
        return snapshotMap(sourceCounts);
    }

    public List<String> getAvailableSources() {
        return sourceCounts.keySet().stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    public List<String> getAvailableLevels() {
        return levelCounts.keySet().stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    private Map<String, Long> snapshotMap(ConcurrentMap<String, AtomicLong> counts) {
        return counts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
