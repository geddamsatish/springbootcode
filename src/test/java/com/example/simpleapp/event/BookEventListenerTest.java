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
 * Tests the Redis side of the CDC pipeline: a Debezium change message for a book
 * must remove that book's cached entry. Uses the real Redis container.
 * The Kafka listener container is switched off, so the test calls the listener directly.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:listenertest",
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
    void updateMessageRemovesCachedBook() {
        Book book = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");
        assertThat(cache.get(book.getId())).isNotNull();

        bookEventListener.handleBookChange(updateMessage(book.getId()));

        assertThat(cache.get(book.getId())).isNull();
    }

    @Test
    void deleteMessageRemovesCachedBook() {
        Book book = bookService.create(new BookRequest("Temp", "Author", 2020));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");

        bookEventListener.handleBookChange(deleteMessage(book.getId()));

        assertThat(cache.get(book.getId())).isNull();
    }

    @Test
    void tombstoneMessageIsIgnored() {
        Book book = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");

        bookEventListener.handleBookChange(null);

        assertThat(cache.get(book.getId())).isNotNull();
    }

    @Test
    void wrappedOldFormatIsStillUnderstood() {
        Book book = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));
        bookService.findById(book.getId());
        Cache cache = cacheManager.getCache("books");

        String wrapped = "{\"payload\":{\"op\":\"u\",\"after\":{\"id\":" + book.getId() + "}}}";
        bookEventListener.handleBookChange(wrapped);

        assertThat(cache.get(book.getId())).isNull();
    }

    @Test
    void changeForBookThatIsNotCachedDoesNotFail() {
        assertThatCode(() -> bookEventListener.handleBookChange(updateMessage(999_999L)))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedMessageDoesNotThrow() {
        assertThatCode(() -> bookEventListener.handleBookChange("not json"))
                .doesNotThrowAnyException();
    }

    private static String updateMessage(long id) {
        return "{\"before\":{\"id\":" + id + ",\"title\":\"Dune\",\"author\":\"Frank Herbert\",\"published_year\":1965},"
                + "\"after\":{\"id\":" + id + ",\"title\":\"Dune 2\",\"author\":\"Frank Herbert\",\"published_year\":1965},"
                + "\"op\":\"u\"}";
    }

    private static String deleteMessage(long id) {
        return "{\"before\":{\"id\":" + id + ",\"title\":\"Temp\",\"author\":\"Author\",\"published_year\":2020},"
                + "\"after\":null,\"op\":\"d\"}";
    }
}
