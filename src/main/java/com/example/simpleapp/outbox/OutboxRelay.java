package com.example.simpleapp.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Sends unpublished outbox rows to Kafka, in id order.
 * A row is marked published only after Kafka confirms the send.
 * On the first failure we stop, so later rows are not sent before earlier ones,
 * and the failed row is retried on the next run.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String TOPIC = "book-changes";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();
        if (pending.isEmpty()) {
            return; // nothing to do; stay quiet every second
        }
        log.info("[OUTBOX] found {} unpublished event(s)", pending.size());

        int sent = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()).get();
                event.markAsPublished();
                sent++;
                log.info("[OUTBOX] published outbox row {} ({} for book {}) to topic '{}'",
                        event.getId(), event.getEventType(), event.getAggregateId(), TOPIC);
            } catch (ExecutionException e) {
                log.warn("[OUTBOX] could not publish row {}; stopping, will retry on next run", event.getId(), e);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[OUTBOX] interrupted while publishing; stopping");
                break;
            }
        }
        log.info("[OUTBOX] run finished: {} of {} sent", sent, pending.size());
    }
}
