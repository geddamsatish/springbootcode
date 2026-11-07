package com.example.simpleapp.controller;

import com.example.simpleapp.client.Post;
import com.example.simpleapp.client.PostClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostClient postClient;
    public PostController(PostClient postClient) {
        this.postClient = postClient;
    }

    @GetMapping("/{id}")
    public Post fetchPost( @PathVariable("id") int id){
        return postClient.fetchPost(id);
    }
}
