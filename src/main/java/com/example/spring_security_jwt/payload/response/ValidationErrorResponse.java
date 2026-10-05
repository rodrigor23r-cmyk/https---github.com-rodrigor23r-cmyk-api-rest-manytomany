package com.example.spring_security_jwt.payload.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ValidationErrorResponse {
    private Map<String, String> errors;
    private String message;
}
