package com.example.gsb.schema;

/**
 * 字段数据类型。CONFLICT 表示同一字段出现无法合并的多种类型；
 * UNKNOWN 表示字段全为空值/缺失，暂无法推断。
 */
public enum FieldType {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATETIME,
    STRING,
    ENUM,
    CONFLICT,
    UNKNOWN
}
