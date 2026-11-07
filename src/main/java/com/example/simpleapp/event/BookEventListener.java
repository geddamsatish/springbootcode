package com.example.simpleapp.event;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookEventListener {
    private final CacheManager cacheManager;
    public BookEventListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    @KafkaListener(topics="book-changes")
    public void handleBookEvent(BookEvent bookEvent) {
       Cache cache =  cacheManager.getCache("books");
       if(cache!=null){
           cache.evict(bookEvent.bookId());
       }
    }
}
