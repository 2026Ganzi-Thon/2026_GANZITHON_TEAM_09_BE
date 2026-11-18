package com.haengunping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HaengunpingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaengunpingApplication.class, args);
    }
}
