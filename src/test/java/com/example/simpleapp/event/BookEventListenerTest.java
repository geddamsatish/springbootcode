package com.example.simpleapp.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.service.BookService;

/**
 * Tests the Redis side of the pipeline: a change event must remove the cached book.
 * Uses the real Redis container (spring.cache.type=redis). The Kafka listener and the
 * outbox relay are switched off, so the test calls the listener directly.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:listenertest",
        "app.outbox.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class BookEventListenerTest {

    @Autowired
    private BookEventListener bookEventListener;

    @Autowired
    private BookService bookService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("books").clear();
    }

    @Test
    void updatedEventRemovesCachedBook() {
        Book book = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");
        assertThat(cache.get(book.getId())).isNotNull();

        bookEventListener.handleBookEvent(
                new BookEvent("UPDATED", book.getId(), "Dune", "Frank Herbert", 1965));

        assertThat(cache.get(book.getId())).isNull();
    }

    @Test
    void deletedEventRemovesCachedBook() {
        Book book = bookService.create(new BookRequest("Temp", "Author", 2020));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");

        bookEventListener.handleBookEvent(
                new BookEvent("DELETED", book.getId(), "Temp", "Author", 2020));

        assertThat(cache.get(book.getId())).isNull();
    }

    @Test
    void eventForBookThatIsNotCachedDoesNotFail() {
        assertThatCode(() -> bookEventListener.handleBookEvent(
                new BookEvent("CREATED", 999_999L, "Missing", "Nobody", 2000)))
                .doesNotThrowAnyException();
    }
}
