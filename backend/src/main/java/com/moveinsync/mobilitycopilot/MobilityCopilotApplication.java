package com.moveinsync.mobilitycopilot;

import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.config.SarvamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MobilityDataProperties.class, SarvamProperties.class,
        com.moveinsync.mobilitycopilot.config.AnalyticsProperties.class})
public class MobilityCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobilityCopilotApplication.class, args);
    }
}
