package com.example.auditpanel.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

@RestController
public class SqlController {

    @GetMapping(value = "/api/sql/schema", produces = "text/plain")
    public ResponseEntity<byte[]> getSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("sql/schema.sql");
        if (!resource.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(("schema.sql not found").getBytes());
        }
        try (InputStream is = resource.getInputStream()) {
            byte[] content = StreamUtils.copyToByteArray(is);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDisposition(ContentDisposition.attachment().filename("schema.sql").build());
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        }
    }
}
