package com.moveinsync.mobilitycopilot.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
class MobilityCapabilitiesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsTheGovernedLocalFallbackAsReleaseReady() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.mobilityCapabilities.status").value("UP"))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.releaseReady").value(true))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.operatingMode").value("FULL"))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.capabilities.governedMetrics.status")
                        .value("AVAILABLE"))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.capabilities.workflowEngine.status")
                        .value("AVAILABLE"))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.capabilities.authorization.status")
                        .value("AVAILABLE"))
                .andExpect(jsonPath("$.components.mobilityCapabilities.details.capabilities.businessAudit.status")
                        .value("AVAILABLE"));
    }
}
