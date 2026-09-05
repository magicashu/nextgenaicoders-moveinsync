package com.moveinsync.mobilitycopilot.config;

import com.moveinsync.mobilitycopilot.workflow.adapter.ollama.OllamaLanguageModelAdapter;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaProperties.class)
@ConditionalOnProperty(prefix = "mobility.ai", name = "provider", havingValue = "ollama")
class OllamaConfiguration {
    @Bean
    LanguageModelPort ollamaLanguageModelPort(OllamaProperties properties) {
        return new OllamaLanguageModelAdapter(properties);
    }
}
