package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;

import java.time.Instant;
import java.util.List;

public interface LogSearchService {
    List<LogEvent> search(String source, String level, Instant since, String contains, int limit);
}
