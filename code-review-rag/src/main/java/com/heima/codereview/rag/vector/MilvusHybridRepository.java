package com.heima.codereview.rag.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.heima.codereview.rag.embedding.EmbeddingService;
import com.heima.codereview.rag.model.ReviewRecord;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MilvusHybridRepository {

    private static final Logger log = LoggerFactory.getLogger(MilvusHybridRepository.class);
    private static final String ID_FIELD = "id";
    private static final String RECORD_ID_FIELD = "recordId";
    private static final String REVIEW_ID_FIELD = "reviewId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String PROJECT_ID_FIELD = "projectId";
    private static final String SOURCE_TYPE_FIELD = "sourceType";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String CONTENT_FIELD = "content";
    private static final String DENSE_VECTOR_FIELD = "dense_vector";
    private static final String SPARSE_VECTOR_FIELD = "sparse_vector";
    private static final String REVIEW_SOURCE_TYPE = "review";
    private static final int MAX_VARCHAR_LENGTH = 8192;

    private final EmbeddingService embeddingService;
    private final boolean enabled;
    private final String uri;
    private final String username;
    private final String password;
    private final String databaseName;
    private final String collectionName;
    private final long connectTimeoutMs;
    private final long rpcDeadlineMs;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    private volatile MilvusClientV2 client;

    public MilvusHybridRepository(EmbeddingService embeddingService,
                                  @Value("${code-review.rag.hybrid.enabled:true}") boolean enabled,
                                  @Value("${spring.ai.vectorstore.milvus.client.host:localhost}") String host,
                                  @Value("${spring.ai.vectorstore.milvus.client.port:19530}") int port,
                                  @Value("${spring.ai.vectorstore.milvus.client.connect-timeout-ms:10000}") long connectTimeoutMs,
                                  @Value("${spring.ai.vectorstore.milvus.client.rpc-deadline-ms:30000}") long rpcDeadlineMs,
                                  @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
                                  @Value("${spring.ai.vectorstore.milvus.collection-name:code_review_knowledge}") String baseCollectionName,
                                  @Value("${code-review.rag.hybrid.collection-name:}") String hybridCollectionName,
                                  @Value("${spring.ai.vectorstore.milvus.client.username:}") String username,
                                  @Value("${spring.ai.vectorstore.milvus.client.password:}") String password) {
        this.embeddingService = embeddingService;
        this.enabled = enabled;
        this.uri = normalizeUri(host, port);
        this.connectTimeoutMs = connectTimeoutMs;
        this.rpcDeadlineMs = rpcDeadlineMs;
        this.databaseName = blankToDefault(databaseName, "default");
        this.collectionName = hybridCollectionName == null || hybridCollectionName.isBlank()
                ? baseCollectionName + "_hybrid"
                : hybridCollectionName.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void insert(ReviewRecord record, Map<String, Object> metadata) {
        if (!enabled || record == null || record.content() == null || record.content().isBlank()) {
            return;
        }
        try {
            ensureSchema();
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, safe(record.id()));
            row.addProperty(RECORD_ID_FIELD, safe(record.id()));
            row.addProperty(REVIEW_ID_FIELD, safe(record.reviewId()));
            row.addProperty(SESSION_ID_FIELD, safe(record.sessionId()));
            row.addProperty(PROJECT_ID_FIELD, safe(record.projectId()));
            row.addProperty(SOURCE_TYPE_FIELD, REVIEW_SOURCE_TYPE);
            row.addProperty(TIMESTAMP_FIELD, record.timestamp());
            row.addProperty(CONTENT_FIELD, trimForMilvus(record.content()));
            row.add(DENSE_VECTOR_FIELD, toJsonArray(embeddingService.embed(record.content())));
            client().insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(row))
                    .build());
        } catch (Exception e) {
            log.warn("Native Milvus hybrid insert skipped. collection={}, recordId={}, reason={}",
                    collectionName, record.id(), e.getMessage());
        }
    }

    public List<ReviewRecord> search(String query, String projectId, String sessionId, int topK) {
        if (!enabled || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            ensureSchema();
            int candidateLimit = Math.max(topK * 3, topK);
            String expr = buildFilter(projectId, sessionId);
            AnnSearchReq denseRequest = AnnSearchReq.builder()
                    .vectorFieldName(DENSE_VECTOR_FIELD)
                    .vectors(List.of(new FloatVec(embeddingService.embed(query))))
                    .topK(candidateLimit)
                    .expr(expr)
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();
            AnnSearchReq sparseRequest = AnnSearchReq.builder()
                    .vectorFieldName(SPARSE_VECTOR_FIELD)
                    .vectors(List.of(new EmbeddedText(query)))
                    .topK(candidateLimit)
                    .expr(expr)
                    .metricType(IndexParam.MetricType.BM25)
                    .build();
            SearchResp response = client().hybridSearch(HybridSearchReq.builder()
                    .collectionName(collectionName)
                    .searchRequests(List.of(denseRequest, sparseRequest))
                    .ranker(new WeightedRanker(List.of(0.70f, 0.30f)))
                    .topK(Math.max(1, topK))
                    .outFields(List.of(RECORD_ID_FIELD, REVIEW_ID_FIELD, SESSION_ID_FIELD, PROJECT_ID_FIELD, CONTENT_FIELD, TIMESTAMP_FIELD))
                    .build());
            return toRecords(response);
        } catch (Exception e) {
            log.warn("Native Milvus hybrid search unavailable, fallback will be used. collection={}, reason={}",
                    collectionName, e.getMessage());
            return List.of();
        }
    }

    public void deleteByReviewId(String reviewId) {
        if (!enabled || reviewId == null || reviewId.isBlank()) {
            return;
        }
        try {
            ensureSchema();
            client().delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(REVIEW_ID_FIELD + " == \"" + escapeExpr(reviewId) + "\"")
                    .build());
        } catch (Exception e) {
            log.warn("Native Milvus hybrid delete skipped. collection={}, reviewId={}, reason={}",
                    collectionName, reviewId, e.getMessage());
        }
    }

    private List<ReviewRecord> toRecords(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        Map<String, ReviewRecord> records = new LinkedHashMap<>();
        for (List<SearchResp.SearchResult> batch : response.getSearchResults()) {
            for (SearchResp.SearchResult result : batch) {
                Map<String, Object> entity = result.getEntity();
                if (entity == null) {
                    continue;
                }
                String recordId = asString(entity.getOrDefault(RECORD_ID_FIELD, result.getId()));
                records.putIfAbsent(recordId, new ReviewRecord(
                        recordId,
                        asString(entity.get(REVIEW_ID_FIELD)),
                        asString(entity.get(SESSION_ID_FIELD)),
                        asString(entity.get(CONTENT_FIELD)),
                        asString(entity.get(PROJECT_ID_FIELD)),
                        asLong(entity.get(TIMESTAMP_FIELD))
                ));
            }
        }
        return new ArrayList<>(records.values());
    }

    private void ensureSchema() {
        if (schemaReady.get()) {
            return;
        }
        synchronized (schemaReady) {
            if (schemaReady.get()) {
                return;
            }
            MilvusClientV2 current = client();
            if (!current.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build())) {
                CreateCollectionReq.CollectionSchema schema = current.createSchema();
                schema.addField(AddFieldReq.builder()
                        .fieldName(ID_FIELD)
                        .dataType(DataType.VarChar)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .maxLength(128)
                        .build());
                schema.addField(varcharField(RECORD_ID_FIELD, 128));
                schema.addField(varcharField(REVIEW_ID_FIELD, 128));
                schema.addField(varcharField(SESSION_ID_FIELD, 128));
                schema.addField(varcharField(PROJECT_ID_FIELD, 256));
                schema.addField(varcharField(SOURCE_TYPE_FIELD, 32));
                schema.addField(AddFieldReq.builder().fieldName(TIMESTAMP_FIELD).dataType(DataType.Int64).build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(CONTENT_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(MAX_VARCHAR_LENGTH)
                        .enableAnalyzer(true)
                        .enableMatch(true)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(DENSE_VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(embeddingService.getDimension())
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(SPARSE_VECTOR_FIELD)
                        .dataType(DataType.SparseFloatVector)
                        .build());
                schema.addFunction(CreateCollectionReq.Function.builder()
                        .name("content_bm25")
                        .functionType(FunctionType.BM25)
                        .inputFieldNames(List.of(CONTENT_FIELD))
                        .outputFieldNames(List.of(SPARSE_VECTOR_FIELD))
                        .build());

                current.createCollection(CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(schema)
                        .indexParams(List.of(
                                IndexParam.builder()
                                        .fieldName(DENSE_VECTOR_FIELD)
                                        .indexType(IndexParam.IndexType.HNSW)
                                        .metricType(IndexParam.MetricType.COSINE)
                                        .extraParams(Map.of("M", 16, "efConstruction", 128))
                                        .build(),
                                IndexParam.builder()
                                        .fieldName(SPARSE_VECTOR_FIELD)
                                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                        .metricType(IndexParam.MetricType.BM25)
                                        .build()))
                        .build());
            }
            current.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .sync(true)
                    .build());
            schemaReady.set(true);
        }
    }

    private AddFieldReq varcharField(String fieldName, int maxLength) {
        return AddFieldReq.builder()
                .fieldName(fieldName)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .build();
    }

    private MilvusClientV2 client() {
        MilvusClientV2 current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                        .uri(uri)
                        .dbName(databaseName)
                        .connectTimeoutMs(connectTimeoutMs)
                        .rpcDeadlineMs(rpcDeadlineMs);
                if (!username.isBlank()) {
                    builder.username(username);
                }
                if (!password.isBlank()) {
                    builder.password(password);
                }
                client = new MilvusClientV2(builder.build());
            }
            return client;
        }
    }

    private String buildFilter(String projectId, String sessionId) {
        List<String> filters = new ArrayList<>();
        filters.add(SOURCE_TYPE_FIELD + " == \"" + REVIEW_SOURCE_TYPE + "\"");
        if (projectId != null && !projectId.isBlank()) {
            filters.add(PROJECT_ID_FIELD + " == \"" + escapeExpr(projectId) + "\"");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            filters.add(SESSION_ID_FIELD + " == \"" + escapeExpr(sessionId) + "\"");
        }
        return String.join(" && ", filters);
    }

    private JsonArray toJsonArray(List<Float> values) {
        JsonArray array = new JsonArray();
        for (Float value : values == null ? List.<Float>of() : values) {
            array.add(value == null ? 0f : value);
        }
        return array;
    }

    private String normalizeUri(String host, int port) {
        String rawHost = host == null || host.isBlank() ? "localhost" : host.trim();
        if (rawHost.contains("://")) {
            return rawHost;
        }
        return "http://" + rawHost + ":" + port;
    }

    private String trimForMilvus(String text) {
        String safe = safe(text);
        return safe.length() <= MAX_VARCHAR_LENGTH ? safe : safe.substring(0, MAX_VARCHAR_LENGTH);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String escapeExpr(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Objects.toString(value, "0"));
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }
}
