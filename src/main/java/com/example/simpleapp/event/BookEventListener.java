package com.example.simpleapp.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives book-change events from Kafka and removes the matching Redis entry,
 * so the next read loads the fresh book from Postgres.
 */
@Component
public class BookEventListener {
    private static final Logger log = LoggerFactory.getLogger(BookEventListener.class);

    private final CacheManager cacheManager;

    public BookEventListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @KafkaListener(topics = "book-changes")
    public void handleBookEvent(BookEvent bookEvent) {
        log.info("[KAFKA] received {} event for book {}", bookEvent.eventType(), bookEvent.bookId());

        Cache cache = cacheManager.getCache("books");
        if (cache == null) {
            log.warn("[REDIS] no 'books' cache configured; nothing to evict");
            return;
        }
        if (cache.get(bookEvent.bookId()) == null) {
            log.info("[REDIS] books::{} is not cached; nothing to evict", bookEvent.bookId());
            return;
        }
        cache.evict(bookEvent.bookId());
        log.info("[REDIS] evicted books::{} (next read will go to Postgres)", bookEvent.bookId());
    }
}
