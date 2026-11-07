package com.example.simpleapp.service;

import com.example.simpleapp.config.AppProperties;
import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.exception.BookNotFoundException;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.repository.BookRepository;
import com.example.simpleapp.utils.BookHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {
    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final AppProperties appProperties;
    private final BookRepository bookRepository;
    private final BookHelper bookHelper;

    public BookService(AppProperties appProperties, BookRepository bookRepository, BookHelper bookHelper) {
        this.appProperties = appProperties;
        this.bookRepository = bookRepository;
        this.bookHelper = bookHelper;
    }

    @Transactional(readOnly = true)
    public List<Book> findAll() {
        return bookRepository.findAll(
                PageRequest.of(0, appProperties.maxPageSize(), Sort.by("id"))
        ).getContent();
    }

    /**
     * This body runs ONLY on a cache miss. If the book is already in Redis,
     * Spring returns it and this log line does not appear.
     */
    @Cacheable(value = "books", key = "#id")
    public Book findById(Long id) {
        log.info("[CACHE MISS] books::{} not in Redis; reading from Postgres", id);
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    @Transactional
    public Book create(BookRequest bookRequest) {
        Book savedBook = bookRepository.save(bookHelper.toBook(null, bookRequest));
        log.info("[DB] saved book {} ('{}')", savedBook.getId(), savedBook.getTitle());
        return savedBook;
    }

    @Transactional
    public Book update(Long id, BookRequest bookRequest) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        book.update(bookRequest.title(), bookRequest.author(), bookRequest.publishedYear());
        Book savedBook = bookRepository.save(book);
        log.info("[DB] updated book {} ('{}')", savedBook.getId(), savedBook.getTitle());
        return savedBook;
    }

    @Transactional
    public void delete(Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        bookRepository.delete(book);
        log.info("[DB] deleted book {}", id);
    }

    @Transactional(readOnly = true)
    public List<Book> searchByAuthor(String author) {
        return bookRepository.findByAuthorContainingIgnoreCase(author);
    }
}
