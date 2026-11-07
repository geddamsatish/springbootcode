package com.example.simpleapp.utils;

import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.model.Book;
import org.springframework.stereotype.Component;

@Component
public class BookHelper {
    public Book toBook(Long id, BookRequest bookRequest){
        return new Book(id,bookRequest.title(),bookRequest.author(),bookRequest.publishedYear());
    }
}
