package com.example.gsb.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试辅助：构造样本行（允许 null 值表示空值，缺省 key 表示缺失）。 */
final class TestRows {

    private TestRows() {
    }

    static Map<String, String> row(Object... keyValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], (String) keyValues[i + 1]);
        }
        return row;
    }

    static List<Map<String, String>> rows(Map<String, String>... rows) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map<String, String> row : rows) {
            list.add(row);
        }
        return list;
    }

    /** 生成 n 行，字段 field 依次取 values 中循环的值。 */
    static List<Map<String, String>> repeat(String field, List<String> values, int n) {
        List<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(row(field, values.get(i % values.size())));
        }
        return list;
    }
}
