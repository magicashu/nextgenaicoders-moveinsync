package com.moveinsync.mobilitycopilot.api;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-side check against the frozen OpenAPI 0.1.0 and JSON schemas: the scaffold path is still
 * served with its required header, and the DecisionBrief the API emits has every required property.
 * The six product endpoints are proposed to the Integration Owner in the handoff for OpenAPI 0.2.0.
 */
class OpenApiProviderTest {

    private static Path contracts() {
        for (String candidate : new String[] {"contracts", "../contracts"}) {
            if (Files.isDirectory(Path.of(candidate))) {
                return Path.of(candidate);
            }
        }
        throw new IllegalStateException("contracts directory not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void frozenContractStillDescribesTheDemoEndpoint() throws Exception {
        Map<String, Object> openapi = new Yaml().load(Files.readString(contracts().resolve("openapi/mobility-copilot.yaml")));
        Map<String, Object> paths = (Map<String, Object>) openapi.get("paths");
        assertThat(paths).containsKey("/api/v1/demo/brief");
        Map<String, Object> get = (Map<String, Object>) ((Map<String, Object>) paths.get("/api/v1/demo/brief")).get("get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) get.get("parameters");
        assertThat(parameters).anyMatch(p -> "X-Business-Unit".equals(p.get("name")) && Boolean.TRUE.equals(p.get("required")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void decisionBriefSchemaRequiredPropertiesArePresentInTheRecord() throws Exception {
        Map<String, Object> schema = new tools.jackson.databind.ObjectMapper().readValue(Files.readString(contracts().resolve("schemas/decision-brief.schema.json")), Map.class);
        List<String> required = (List<String>) schema.get("required");
        var components = java.util.Arrays.stream(com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertThat(components).containsAll(required);
        Map<String, Object> status = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("status");
        assertThat((List<String>) status.get("enum")).contains("AWAITING_APPROVAL", "HEALTHY");
    }
}
