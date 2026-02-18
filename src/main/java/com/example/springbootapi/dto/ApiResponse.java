package com.example.springbootapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private Object data;

    public ApiResponse(String message) {
        this.timestamp = LocalDateTime.now();
        this.status = 200;
        this.message = message;
        this.data = null;
    }

    public ApiResponse(int status, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.data = null;
    }

    public ApiResponse(int status, String message, Object data) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.data = data;
    }
}
