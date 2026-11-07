package com.example.simpleapp.event;

public record BookEvent(
        String eventType,
        Long bookId,
        String title,
        String author,
        int publishedYear
){}
