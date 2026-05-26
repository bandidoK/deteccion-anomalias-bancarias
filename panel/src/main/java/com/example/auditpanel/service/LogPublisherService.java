package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;

public interface LogPublisherService {
    void publish(LogEvent event);
}
