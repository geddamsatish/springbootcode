package com.example.simpleapp.controller;

import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }
    @GetMapping
    public List<Book> getAllBooks(){
        return bookService.findAll();
    }

    @GetMapping("/{id}")
    public Book findById(@PathVariable("id") Long id){
        return bookService.findById(id);
    }

    @PostMapping
    public ResponseEntity<Book> create(@Valid @RequestBody BookRequest bookRequest){
        Book book = bookService.create(bookRequest);
        return ResponseEntity.created(URI.create("/api/books/"+book.getId())).body(book);
    }

    @PutMapping("/{id}")
    public Book update(@PathVariable("id") Long id, @Valid @RequestBody BookRequest bookRequest){
        return bookService.update(id, bookRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable("id") Long id){
        bookService.delete(id);
    }

    @GetMapping("/search")
    public List<Book> searchByAuthor(@RequestParam("author") String author){
        return bookService.searchByAuthor(author);
    }

}
