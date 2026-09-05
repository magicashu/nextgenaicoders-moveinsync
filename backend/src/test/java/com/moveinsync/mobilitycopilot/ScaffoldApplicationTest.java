package com.moveinsync.mobilitycopilot;

import com.moveinsync.mobilitycopilot.api.ScaffoldStatusController;
import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import com.moveinsync.mobilitycopilot.reporting.api.DemoBriefController;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScaffoldApplicationTest {
    @Autowired ApplicationContext context;

    @Test
    void bootsWithTheOfficialM01ServiceButWithoutProviderOrLegacyDemoEndpoints() {
        assertThat(context.getBeansOfType(DemoBriefController.class)).isEmpty();
        assertThat(context.getBeansOfType(GovernedMetricService.class)).hasSize(1);
        assertThat(context.getBeansOfType(LanguageModelPort.class)).isEmpty();
        var status = context.getBean(ScaffoldStatusController.class).capabilities();
        assertThat(status.governedRuntimeReady()).isFalse();
        assertThat(status.implementedGovernedCapabilities()).containsExactly("M01_DELAYED_TRIP_RATE");
    }
}
