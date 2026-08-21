package com.shipflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShipFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipFlowApplication.class, args);
    }
}
