package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "auditpanel.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchLogPublisherService implements LogPublisherService {
    private final ObjectMapper objectMapper;
    private final RestHighLevelClient client;
    private final String indexName;

    public ElasticsearchLogPublisherService(@Value("${spring.elasticsearch.rest.uris}") String uris,
                                            @Value("${auditpanel.elasticsearch.index:panel-auditoria-logs}") String indexName) {
        this.objectMapper = new ObjectMapper();
        this.client = new RestHighLevelClient(RestClient.builder(parseHosts(uris)));
        this.indexName = indexName;
    }

    private static HttpHost[] parseHosts(String uris) {
        return java.util.Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);
    }

    @PostConstruct
    public void createIndexIfNeeded() throws IOException {
        // Elasticsearch auto-crea índices por nombre si no existen.
    }

    @PreDestroy
    public void closeClient() throws IOException {
        client.close();
    }

    @Override
    public void publish(LogEvent event) {
        if (event == null) {
            return;
        }
        try {
            Map<String, Object> source = objectMapper.convertValue(event, Map.class);
            Object timestamp = source.get("timestamp");
            if (timestamp instanceof Instant) {
                source.put("timestamp", timestamp.toString());
            }
            IndexRequest request = new IndexRequest(indexName)
                    .id(event.getId())
                    .source(source);
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException ex) {
            // En producción se debería registrar el error en un logger.
            ex.printStackTrace();
        }
    }
}
