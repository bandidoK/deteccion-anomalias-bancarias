package com.example.auditpanel.service;

import com.example.auditpanel.model.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "auditpanel.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchLogSearchService implements LogSearchService {
    private final ObjectMapper objectMapper;
    private final RestHighLevelClient client;
    private final String indexName;

    public ElasticsearchLogSearchService(@Value("${spring.elasticsearch.rest.uris}") String uris,
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

    @Override
    public List<LogEvent> search(String source, String level, Instant since, String contains, int limit) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .size(limit)
                .sort("timestamp", SortOrder.DESC);

        BoolQueryBuilder query = QueryBuilders.boolQuery();
        boolean hasCriteria = false;
        if (source != null && !source.isBlank()) {
            query.must(QueryBuilders.matchQuery("source", source));
            hasCriteria = true;
        }
        if (level != null && !level.isBlank()) {
            query.must(QueryBuilders.matchQuery("level", level));
            hasCriteria = true;
        }
        if (since != null) {
            query.must(QueryBuilders.rangeQuery("timestamp").gte(since.toString()));
            hasCriteria = true;
        }
        if (contains != null && !contains.isBlank()) {
            MatchQueryBuilder messageMatch = QueryBuilders.matchQuery("message", contains);
            query.must(messageMatch);
            hasCriteria = true;
        }
        if (!hasCriteria) {
            query = QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery());
        }

        sourceBuilder.query(query);
        SearchRequest searchRequest = new SearchRequest(indexName).source(sourceBuilder);

        try {
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            List<LogEvent> events = new ArrayList<>();
            response.getHits().forEach(hit -> {
                Map<String, Object> sourceMap = hit.getSourceAsMap();
                LogEvent event = objectMapper.convertValue(sourceMap, LogEvent.class);
                events.add(event);
            });
            return events;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @PreDestroy
    public void closeClient() throws IOException {
        client.close();
    }
}
