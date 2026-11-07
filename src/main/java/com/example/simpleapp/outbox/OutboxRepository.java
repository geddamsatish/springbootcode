package com.example.simpleapp.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
   List<OutboxEvent> findTop100ByPublishedFalseOrderByIdAsc();
}

