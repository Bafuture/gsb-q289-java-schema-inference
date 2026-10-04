package com.example.gsb.schemainfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test helper for building row-oriented batches without Map.of null restrictions. */
final class Rows {

    private Rows() {
    }

    static Map<String, String> row(Object... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], (String) keyValues[i + 1]);
        }
        return map;
    }

    static List<Map<String, String>> column(String field, String... values) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String value : values) {
            rows.add(row(field, value));
        }
        return rows;
    }
}
