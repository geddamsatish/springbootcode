package com.example.simpleapp.repository;

import com.example.simpleapp.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface BookRepository extends JpaRepository<Book,Long> {
    List<Book> findByAuthorContainingIgnoreCase(String author);
}
