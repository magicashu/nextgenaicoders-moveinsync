package com.moveinsync.mobilitycopilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Jackson 2 is isolated to the structured language-model contract on Spring Boot 4. */
@Configuration(proxyBeanMethods = false)
class StructuredJsonConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper structuredModelObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
