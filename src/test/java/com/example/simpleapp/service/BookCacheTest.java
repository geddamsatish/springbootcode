package com.example.simpleapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.exception.BookNotFoundException;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.repository.BookRepository;

/**
 * Full context test: caching only works through Spring's proxy, so this test
 * must start the application context (a plain "new BookService" would skip the cache).
 * The repository is a SPY: it behaves like the real one but records every call.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cachetest")
class BookCacheTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private BookRepository bookRepository;

    @BeforeEach
    void resetCacheAndCounts() {
        cacheManager.getCache("books").clear();
        clearInvocations(bookRepository);
    }

    @Test
    void secondLookupComesFromCache() {
        Book created = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));

        bookService.findById(created.getId());
        bookService.findById(created.getId());

        verify(bookRepository, times(1)).findById(created.getId());
    }

    @Test
    void updateRefreshesCachedBook() {
        Book created = bookService.create(new BookRequest("Old title", "Author", 2000));
        bookService.findById(created.getId());

        bookService.update(created.getId(), new BookRequest("New title", "Author", 2000));

        assertThat(bookService.findById(created.getId()).getTitle()).isEqualTo("New title");
    }

    @Test
    void deleteEvictsCachedBook() {
        Book created = bookService.create(new BookRequest("Temp", "Author", 2020));
        bookService.findById(created.getId());

        bookService.delete(created.getId());

        assertThatThrownBy(() -> bookService.findById(created.getId()))
                .isInstanceOf(BookNotFoundException.class);
    }
}
