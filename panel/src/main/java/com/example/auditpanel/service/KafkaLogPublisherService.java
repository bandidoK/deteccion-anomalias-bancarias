package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "auditpanel.kafka", name = "enabled", havingValue = "true")
public class KafkaLogPublisherService implements LogPublisherService {
    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final String topic;

    public KafkaLogPublisherService(KafkaTemplate<String, LogEvent> kafkaTemplate,
                                    @Value("${auditpanel.kafka.topic:panel-auditoria-logs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(LogEvent event) {
        if (event != null) {
            kafkaTemplate.send(topic, event.getId(), event);
        }
    }
}
