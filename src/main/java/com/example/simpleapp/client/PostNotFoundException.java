package com.example.simpleapp.client;


public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(int id) {
        super("Post with id " + id + " was not found");
    }
}
