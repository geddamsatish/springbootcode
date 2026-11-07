package com.example.simpleapp.service;

import com.example.simpleapp.config.AppProperties;
import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.event.BookEvent;
import com.example.simpleapp.exception.BookNotFoundException;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.outbox.OutboxEvent;
import com.example.simpleapp.outbox.OutboxRepository;
import com.example.simpleapp.repository.BookRepository;
import com.example.simpleapp.utils.BookHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BookService {
    private final AppProperties appProperties;
    private final BookRepository bookRepository;
    private final BookHelper bookHelper;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BookService(AppProperties appProperties, BookRepository bookRepository, BookHelper bookHelper,
                       OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.bookRepository = bookRepository;
        this.bookHelper = bookHelper;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Book> findAll() {
        return bookRepository.findAll(
                PageRequest.of(0, appProperties.maxPageSize(), Sort.by("id"))
        ).getContent();
    }

    @Cacheable(value = "books", key = "#id")
    public Book findById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    @Transactional
    public Book create(BookRequest bookRequest) {
        Book savedBook = bookRepository.save(bookHelper.toBook(null,bookRequest));
        recordEvent("CREATED", savedBook);
        return savedBook;
    }

    @Transactional
    public Book update(Long id, BookRequest bookRequest) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        book.update(bookRequest.title(), bookRequest.author(), bookRequest.publishedYear());
        Book savedBook = bookRepository.save(book);
        recordEvent("UPDATED", savedBook);
        return savedBook;
    }

    @Transactional
    public void delete(Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        recordEvent("DELETED", book);
        bookRepository.delete(book);
    }

    @Transactional(readOnly = true)
    public List<Book> searchByAuthor(String author) {
        return bookRepository.findByAuthorContainingIgnoreCase(author);
    }

    /** Stores the change as an outbox row in the current transaction. */
    private void recordEvent(String eventType, Book book) {
        BookEvent bookEvent = new BookEvent(eventType, book.getId(), book.getTitle(),
                book.getAuthor(), book.getPublishedYear());
        try {
            String payload = objectMapper.writeValueAsString(bookEvent);
            outboxRepository.save(new OutboxEvent(String.valueOf(book.getId()), eventType, payload, Instant.now(), false));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize book event", e);
        }
    }
}
