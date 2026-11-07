package com.example.simpleapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SimpleSpringAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleSpringAppApplication.class, args);
    }
}
