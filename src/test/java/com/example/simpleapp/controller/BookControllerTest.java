package com.example.simpleapp.controller;

import com.example.simpleapp.model.Book;
import com.example.simpleapp.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Test
    void getBookByIdReturns200AndBookTitle() throws Exception {
        when(bookService.findById(1L))
                .thenReturn(new Book(1L, "Effective Java", "Joshua Bloch", 2018));

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Effective Java"));
    }

    @Test
    void createBookReturns201AndLocationHeader() throws Exception {
        when(bookService.create(any()))
                .thenReturn(new Book(1L, "Effective Java", "Joshua Bloch", 2018));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Effective Java",
                                  "author": "Joshua Bloch",
                                  "publishedYear": 2018
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createBookWithEmptyTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "author": "Joshua Bloch",
                                  "publishedYear": 2018
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }
}