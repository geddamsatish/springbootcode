package com.example.simpleapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.simpleapp.config.AppProperties;
import com.example.simpleapp.dto.BookRequest;
import com.example.simpleapp.exception.BookNotFoundException;
import com.example.simpleapp.model.Book;
import com.example.simpleapp.outbox.OutboxEvent;
import com.example.simpleapp.outbox.OutboxRepository;
import com.example.simpleapp.repository.BookRepository;
import com.example.simpleapp.utils.BookHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit test with MOCK repositories. No database and no Spring context:
 * we control what the repositories return and check how the service reacts.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("Bookshelf", "1.0.0", 2);
        bookService = new BookService(props, bookRepository, new BookHelper(), outboxRepository, new ObjectMapper());
    }

    @Test
    void findAllRequestsMaxPageSizeFromRepository() {
        Book book = new Book(1L, "Clean Code", "Robert Martin", 2008);
        when(bookRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));

        List<Book> result = bookService.findAll();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(2);
        assertThat(result).containsExactly(book);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.findById(99L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void createSavesBookWithoutId() {
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book created = bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));

        assertThat(created.getId()).isNull();
        assertThat(created.getTitle()).isEqualTo("Dune");
    }

    @Test
    void createWritesCreatedEventToOutbox() {
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookService.create(new BookRequest("Dune", "Frank Herbert", 1965));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("CREATED");
        assertThat(captor.getValue().getPayload()).contains("\"title\":\"Dune\"");
    }

    @Test
    void updateSavesNewValues() {
        Book existing = new Book(1L, "Old", "Author", 2000);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book updated = bookService.update(1L, new BookRequest("New", "Author", 2020));

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getTitle()).isEqualTo("New");
        assertThat(updated.getPublishedYear()).isEqualTo(2020);
    }

    @Test
    void updateWritesUpdatedEventToOutbox() {
        Book existing = new Book(1L, "Old", "Author", 2000);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookService.update(1L, new BookRequest("New", "Author", 2020));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UPDATED");
        assertThat(captor.getValue().getAggregateId()).isEqualTo("1");
    }

    @Test
    void deleteThrowsWhenMissingAndDoesNotDelete() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.delete(99L))
                .isInstanceOf(BookNotFoundException.class);

        verify(bookRepository, never()).delete(any(Book.class));
    }
}
