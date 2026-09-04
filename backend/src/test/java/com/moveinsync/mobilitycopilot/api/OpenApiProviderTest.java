package com.moveinsync.mobilitycopilot.api;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-side check against OpenAPI 0.2.0 and the frozen JSON schemas.
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
        assertThat(((Map<String, Object>) openapi.get("info")).get("version")).isEqualTo("0.2.0");
        Map<String, Object> paths = (Map<String, Object>) openapi.get("paths");
        assertThat(paths).containsKeys("/api/v1/demo/brief", "/api/v1/briefs/morning", "/api/v1/workflows",
                "/api/v1/workflows/{workflowId}", "/api/v1/questions", "/api/v1/approvals/{approvalId}",
                "/api/v1/approvals/{approvalId}/decision", "/api/v1/audit/{workflowId}");
        Map<String, Object> get = (Map<String, Object>) ((Map<String, Object>) paths.get("/api/v1/demo/brief")).get("get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) get.get("parameters");
        assertThat(parameters).anyMatch(p -> "#/components/parameters/BusinessUnit".equals(p.get("$ref")));
        Map<String, Object> components = (Map<String, Object>) openapi.get("components");
        Map<String, Object> componentParameters = (Map<String, Object>) components.get("parameters");
        Map<String, Object> businessUnit = (Map<String, Object>) componentParameters.get("BusinessUnit");
        assertThat(businessUnit).containsEntry("name", "X-Business-Unit").containsEntry("required", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decisionBriefSchemaRequiredPropertiesArePresentInTheRecord() throws Exception {
        Map<String, Object> schema = new tools.jackson.databind.ObjectMapper().readValue(Files.readString(contracts().resolve("schemas/decision-brief.schema.json")), Map.class);
        List<String> required = (List<String>) schema.get("required");
        var components = java.util.Arrays.stream(com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertThat(components).containsAll(required);
        Map<String, Object> status = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("status");
        assertThat((List<String>) status.get("enum")).contains("AWAITING_APPROVAL", "REPORT_ONLY", "HEALTHY");
    }
}
