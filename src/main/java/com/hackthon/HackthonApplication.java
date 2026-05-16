package com.hackthon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // nécessaire pour @Async dans RoadMapService
public class HackthonApplication {
    public static void main(String[] args) {
        SpringApplication.run(HackthonApplication.class, args);
    }
}