package com.virtualkitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VirtualKitchenApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualKitchenApplication.class, args);
    }
}
