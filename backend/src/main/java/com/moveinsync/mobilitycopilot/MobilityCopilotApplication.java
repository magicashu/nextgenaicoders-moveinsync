package com.moveinsync.mobilitycopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MobilityCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobilityCopilotApplication.class, args);
    }
}
