package com.example.springbootapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequestDTO {
    @NotBlank(message = "Username is required")
    @Size( min = 3, max = 20)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6)
    private String password;

    @Email(message = "Email should be valid")
    private String email;
}
