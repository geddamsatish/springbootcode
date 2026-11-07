package com.example.simpleapp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class PostClient {
    private final RestClient restClient;
    public PostClient(RestClient restClient){
        this.restClient = restClient;
    }
    public Post fetchPost(int id){
        Post post = null;
        try{
            post =  restClient.get().uri("/posts/" + id).retrieve().body(Post.class);
        }catch (HttpClientErrorException.NotFound ex){
            throw new PostNotFoundException(id);
        }
        return post;
    }
}
