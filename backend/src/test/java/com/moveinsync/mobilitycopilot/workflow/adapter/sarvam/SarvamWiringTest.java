package com.moveinsync.mobilitycopilot.workflow.adapter.sarvam;

import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "mobility.ai.provider=sarvam", "mobility.ai.sarvam.api-key=test-only-key"})
class SarvamWiringTest {
    @Autowired ApplicationContext context;
    @Test void single_provider_selector_wires_the_sarvam_adapter() {
        assertThat(context.getBeansOfType(LanguageModelPort.class)).hasSize(1);
        assertThat(context.getBean(LanguageModelPort.class)).isInstanceOf(SarvamLanguageModelAdapter.class);
    }
}
