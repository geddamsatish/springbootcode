package com.example.simpleapp.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit test with mocks: no Kafka, no database, no Spring context.
 * We control what Kafka "returns" (success or failure) and check which rows get marked published.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Test
    void marksEveryRowPublishedWhenKafkaConfirms() {
        OutboxEvent first = new OutboxEvent("1", "CREATED", "{\"bookId\":1}", Instant.now(), false);
        OutboxEvent second = new OutboxEvent("2", "CREATED", "{\"bookId\":2}", Instant.now(), false);
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("book-changes"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPendingEvents();

        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isTrue();
    }

    @Test
    void sendsRowsInOrder() {
        OutboxEvent first = new OutboxEvent("1", "CREATED", "{\"bookId\":1}", Instant.now(), false);
        OutboxEvent second = new OutboxEvent("2", "UPDATED", "{\"bookId\":2}", Instant.now(), false);
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("book-changes"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPendingEvents();

        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send("book-changes", "1", "{\"bookId\":1}");
        order.verify(kafkaTemplate).send("book-changes", "2", "{\"bookId\":2}");
    }

    @Test
    void stopsAtFirstFailureAndLeavesRowsUnpublished() {
        OutboxEvent first = new OutboxEvent("1", "CREATED", "{\"bookId\":1}", Instant.now(), false);
        OutboxEvent second = new OutboxEvent("2", "CREATED", "{\"bookId\":2}", Instant.now(), false);
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("book-changes"), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPendingEvents();

        assertThat(first.isPublished()).isFalse();
        assertThat(second.isPublished()).isFalse();
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void doesNothingWhenThereIsNothingPending() {
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of());

        relay.publishPendingEvents();

        verify(kafkaTemplate, times(0)).send(anyString(), anyString(), anyString());
    }
}
