package com.usm.workorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderServiceApplication.class, args);
    }
}
