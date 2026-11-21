package com.brim.easyinvoice.db;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * approval_policies.csv writes its conditions in plain English, so they cannot be loaded mechanically.
 * seed/policies.json (src/main/resources) is our hand translation into JSON rules, with the original text
 * kept next to each one.
 *
 * Rules with "add_step" describe approval steps; rules with "enforced_in" name the code that implements
 * them. Approval routing currently implements the tiers in code (ApprovalRoutingNode), not from this table.
 */
final class PolicySeeds {

    private static final String FILE = "seed/policies.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PolicySeeds() {
    }

    /** The policies as rows for SeedLoader to insert (column name -> value). */
    static List<Map<String, Object>> rows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int order = 0;
        for (JsonNode policy : read()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", policy.get("id").asString());
            row.put("rule_type", policy.get("ruleType").asString());
            row.put("rule_json", JSON.writeValueAsString(policy.get("rule")));
            row.put("source_text", policy.get("sourceText").asString());
            row.put("sort_order", order++);
            rows.add(row);
        }
        return rows;
    }

    private static JsonNode read() {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + FILE, e);
        }
    }
}
