package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.observability.TraceRecorder;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.TransitionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.moveinsync.mobilitycopilot.workflow.adapter.sarvam.SarvamLanguageModelAdapter;


/** Integration-owned bindings that keep optional AI assistance fail-safe. */
@Configuration
@EnableConfigurationProperties(SarvamProperties.class)
public class WorkflowCompositionConfiguration {

    @Bean
    @ConditionalOnMissingBean(LanguageModelPort.class)
    public LanguageModelPort languageModel(SarvamProperties sarvam,
            @Value("${mobility.workflow.language-model:auto}") String provider) {
        return switch (provider.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> new LanguageModelPort.Unavailable();
            case "sarvam" -> new SarvamLanguageModelAdapter(sarvam);
            case "auto" -> sarvam.apiKey() == null || sarvam.apiKey().isBlank()
                    ? new LanguageModelPort.Unavailable() : new SarvamLanguageModelAdapter(sarvam);
            default -> throw new IllegalArgumentException("LANGUAGE_MODEL must be auto, sarvam or none");
        };
    }

    @Bean
    @ConditionalOnMissingBean(TransitionListener.class)
    public TransitionListener traceTransitionListener(TraceRecorder recorder) {
        return new com.moveinsync.mobilitycopilot.observability.WorkflowTraceListener(recorder);
    }
}
