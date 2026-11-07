package com.example.simpleapp.client;

public record Post(
        int id,
        int userId,
        String title,
        String body
){}