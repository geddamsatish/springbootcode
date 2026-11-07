package com.example.simpleapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.simpleapp.model.Book;

/**
 * @DataJpaTest starts only the database layer: JPA, Hibernate and an in-memory H2 database.
 * Each test runs in its own transaction, which is rolled back afterwards, so tests don't share data.
 */
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Test
    void saveAssignsId() {
        Book saved = bookRepository.save(new Book(null, "Clean Code", "Robert Martin", 2008));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findByAuthorIgnoresCase() {
        bookRepository.save(new Book(null, "Refactoring", "Martin Fowler", 1999));

        List<Book> result = bookRepository.findByAuthorContainingIgnoreCase("martin");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Refactoring");
    }

    @Test
    void findByAuthorReturnsEmptyWhenNoMatch() {
        bookRepository.save(new Book(null, "Refactoring", "Martin Fowler", 1999));

        assertThat(bookRepository.findByAuthorContainingIgnoreCase("Nobody")).isEmpty();
    }

    @Test
    void deleteRemovesBook() {
        Book saved = bookRepository.save(new Book(null, "Temp", "Author", 2020));

        bookRepository.deleteById(saved.getId());

        assertThat(bookRepository.existsById(saved.getId())).isFalse();
    }
}
