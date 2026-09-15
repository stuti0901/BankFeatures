package com.example.summary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SummaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SummaryApplication.class, args);
    }
}