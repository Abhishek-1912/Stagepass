package com.stagepass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StagepassApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StagepassApiApplication.class, args);
    }
}
