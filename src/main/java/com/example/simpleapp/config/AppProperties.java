package com.example.simpleapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
String name,
String version,
int maxPageSize
) {
}
