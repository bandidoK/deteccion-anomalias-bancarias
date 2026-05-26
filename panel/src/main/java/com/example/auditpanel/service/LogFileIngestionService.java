package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.MappingIterator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class LogFileIngestionService {
    private final ObjectMapper objectMapper;
    private final CsvMapper csvMapper;
    private final CsvSchema csvSchema;

    public LogFileIngestionService() {
        this.objectMapper = new ObjectMapper();
        this.csvMapper = new CsvMapper();
        this.csvSchema = CsvSchema.builder()
                .addColumn("id")
                .addColumn("timestamp")
                .addColumn("source")
                .addColumn("level")
                .addColumn("message")
                .build()
                .withHeader();
    }

    public List<LogEvent> parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return parseCsv(file);
        }
        return parseJson(file);
    }

    private List<LogEvent> parseJson(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        try {
            return objectMapper.readValue(bytes, objectMapper.getTypeFactory().constructCollectionType(List.class, LogEvent.class));
        } catch (IOException ex) {
            LogEvent singleEvent = objectMapper.readValue(bytes, LogEvent.class);
            List<LogEvent> events = new ArrayList<>();
            events.add(singleEvent);
            return events;
        }
    }

    private List<LogEvent> parseCsv(MultipartFile file) throws IOException {
        MappingIterator<LogEvent> iterator = csvMapper.readerFor(LogEvent.class)
                .with(csvSchema)
                .readValues(file.getInputStream());
        List<LogEvent> events = new ArrayList<>();
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        return events;
    }
}
