package com.example.auditpanel.controller;

import com.example.auditpanel.model.AnomalyEntity;
import com.example.auditpanel.service.AnomalyBroadcastService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Controller
@ConditionalOnProperty(name = "auditpanel.sql.enabled", havingValue = "true")
public class AnomalyController {

    private final AnomalyBroadcastService broadcastService;

    public AnomalyController(AnomalyBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @GetMapping("/anomalies")
    public String anomaliesPage() {
        return "anomalies";
    }

    @GetMapping(value = "/api/anomalies/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream() {
        return broadcastService.subscribe();
    }

    @GetMapping("/api/anomalies")
    @ResponseBody
    public List<AnomalyEntity> list() {
        return broadcastService.listAll();
    }
}
