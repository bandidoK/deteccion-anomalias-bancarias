package com.example.auditpanel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuditPanelApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditPanelApplication.class, args);
    }
}
