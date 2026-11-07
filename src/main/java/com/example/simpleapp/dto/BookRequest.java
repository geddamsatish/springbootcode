package com.example.simpleapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookRequest (
        @NotBlank(message = "Title cannot be blank")
        @Size(max = 200, message = "Max limit for title is 200 characters")
        String title,
        @NotBlank(message = "Author cannot be blank")
        String author,
        @Min(value = 1450, message = "PublishedYear cannot be less than 1450")
        @Max(value = 2100, message = "PublishedYear cannot be more than 2100")
        int publishedYear

){}
