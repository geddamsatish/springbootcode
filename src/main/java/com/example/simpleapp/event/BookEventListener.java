package com.example.simpleapp.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads Debezium change messages for the Postgres 'book' table and removes the matching
 * Redis entry. The next read then loads the fresh row from Postgres.
 *
 * Message format (plain JSON, no schema): op is "c", "u", "r" (snapshot) or "d";
 * the row is in "after" (or "before" for deletes).
 */
@Component
public class BookEventListener {
    private static final Logger log = LoggerFactory.getLogger(BookEventListener.class);
    private static final String CACHE_NAME = "books";

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public BookEventListener(CacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "bookshelf.public.book")
    public void handleBookChange(String message) {
        if (message == null) {
            // Tombstone: Debezium sends one after each delete (for log compaction). Nothing to do.
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            // Older messages wrap the change in "payload". Current messages have op/before/after at the top.
            JsonNode change = root.has("payload") ? root.get("payload") : root;
            String op = change.path("op").asText();
            JsonNode row = "d".equals(op) ? change.path("before") : change.path("after");
            if (!row.hasNonNull("id")) {
                log.warn("[KAFKA] change with op={} has no row id; ignored", op);
                return;
            }
            long bookId = row.get("id").asLong();
            log.info("[KAFKA] received op={} for book {}", op, bookId);
            evictCachedBook(bookId);
        } catch (JsonProcessingException e) {
            // Log and skip, so one bad message doesn't block the topic.
            log.error("[KAFKA] could not parse change message; skipping: {}", message, e);
        }
    }

    private void evictCachedBook(long bookId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            log.warn("[REDIS] no '{}' cache configured; nothing to evict", CACHE_NAME);
            return;
        }
        if (cache.get(bookId) == null) {
            log.info("[REDIS] books::{} is not cached; nothing to evict", bookId);
            return;
        }
        cache.evict(bookId);
        log.info("[REDIS] evicted books::{} (next read will go to Postgres)", bookId);
    }
}
