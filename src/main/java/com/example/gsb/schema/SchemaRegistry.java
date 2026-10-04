package com.example.gsb.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模式注册表（门面）：跨批次摄入样本，维护累积模式。
 * 后续批次与既有模式不一致时按策略扩展或明确拒绝，绝不静默改类型。
 */
public final class SchemaRegistry {

    private final SchemaInferrer inferrer;
    private final SchemaMerger merger;
    private Schema schema;
    private int batchCount;

    public SchemaRegistry() {
        this(InferenceConfig.DEFAULT, MergePolicy.EXPAND);
    }

    public SchemaRegistry(InferenceConfig config, MergePolicy policy) {
        this.inferrer = new SchemaInferrer(config);
        this.merger = new SchemaMerger(policy);
    }

    /**
     * 摄入一批样本：先推断本批模式，再与既有模式合并并更新注册表。
     *
     * @return 合并结果（含冲突、拒绝与警告信息）
     */
    public SchemaMergeResult ingest(List<? extends Map<String, String>> rows) {
        List<String> warnings = new ArrayList<>();
        Schema incoming = inferrer.inferBatch(rows, batchCount, warnings);
        SchemaMergeResult result = merger.merge(schema, incoming, batchCount, warnings);
        this.schema = result.schema();
        this.batchCount++;
        return result;
    }

    /** 当前累积模式（未摄入任何批次时为 null）。 */
    public Optional<Schema> currentSchema() {
        return Optional.ofNullable(schema);
    }

    public int batchCount() {
        return batchCount;
    }

    /** 当前模式的统计信息。 */
    public SchemaStats stats() {
        if (schema == null) {
            return new SchemaStats(0, Map.of(), 0, 0);
        }
        return SchemaStats.of(schema);
    }
}
