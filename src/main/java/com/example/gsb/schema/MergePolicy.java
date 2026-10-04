package com.example.gsb.schema;

/**
 * 跨批次合并策略：
 * STRICT  —— 新批次类型必须与既有模式完全一致，否则明确拒绝（不修改既有模式）；
 * EXPAND  —— 允许按优先级扩展模式（如 INTEGER -> DECIMAL），无法扩展时报冲突。
 */
public enum MergePolicy {
    STRICT,
    EXPAND
}
