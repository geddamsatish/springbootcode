package com.example.simpleapp.service;

import com.example.simpleapp.config.AppProperties;
import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.exception.BookNotFoundException;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.repository.BookRepository;
import com.example.simpleapp.utils.BookHelper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {
    private final AppProperties appProperties;
    private final BookRepository bookRepository;
    private final BookHelper bookHelper;
    public BookService(AppProperties appProperties, BookRepository bookRepository, BookHelper bookHelper) {
        this.appProperties = appProperties;
        this.bookRepository = bookRepository;
        this.bookHelper = bookHelper;
    }
    @Transactional(readOnly = true)
    public List<Book> findAll(){
       return bookRepository.findAll(
               PageRequest.of(0, appProperties.maxPageSize(),Sort.by("id"))
       ).getContent();
    }
    @Cacheable(value = "books", key = "#id")
    public Book findById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    public Book create(BookRequest bookRequest) {
        Book book = bookHelper.toBook(null, bookRequest);
        return bookRepository.save(book);
    }
    @Transactional
    @CachePut(value = "books", key = "#id")
    public Book update(Long id, BookRequest bookRequest) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        book.update(bookRequest.title(), bookRequest.author(), bookRequest.publishedYear());
        return bookRepository.save(book);
    }
    @Transactional
    @CacheEvict(value = "books", key = "#id")
    public void delete(Long id) {
        if(bookRepository.existsById(id)){
            bookRepository.deleteById(id);
        } else  {
            throw new BookNotFoundException(id);
        }
    }
    @Transactional(readOnly = true)
    public List<Book> searchByAuthor(String author){
        return bookRepository.findByAuthorContainingIgnoreCase(author);
    }
}
