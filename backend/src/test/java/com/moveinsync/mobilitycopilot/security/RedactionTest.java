package com.moveinsync.mobilitycopilot.security;

import com.moveinsync.mobilitycopilot.observability.Redaction;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionTest {

    @Test
    void secretsPiiAndModelTextNeverLeaveTheProcess() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("mobility.business_unit", "pinnacle-Slc");
        raw.put("prompt", "You are the supervisor…");
        raw.put("gen_ai.completion", "{\"tasks\": []}");
        raw.put("authorization", "Basic cGstbGYtMTIzOnNrLWxmLTQ1Ng==");
        raw.put("stwid", "149530");
        raw.put("note", "contact rider at rider.one@example.com or +91 98765 43210; key sk-lf-abcdef123456 jdbc:postgresql://h/db?password=hunter2");

        Map<String, String> safe = Redaction.attributes(raw);

        assertThat(safe).containsEntry("mobility.business_unit", "pinnacle-Slc");
        assertThat(safe).doesNotContainKeys("prompt", "gen_ai.completion", "authorization");
        assertThat(safe.get("stwid")).startsWith("tok_").doesNotContain("149530");
        assertThat(safe.get("note")).doesNotContain("example.com").doesNotContain("98765").doesNotContain("sk-lf-abcdef123456").doesNotContain("hunter2");
        assertThat(Redaction.token("149530")).isEqualTo(Redaction.token("149530"));
        assertThat(Redaction.isForbiddenKey("system_prompt")).isTrue();
        assertThat(Redaction.text("x".repeat(5000))).hasSizeLessThan(2100);
    }
}
