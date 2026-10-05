package com.example.spring_security_jwt.jwt;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor // F7: para inyectar el ObjectMapper
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthEntryPointJwt.class);

    // F7: el mismo ObjectMapper que usa Spring MVC, para que el JSON tenga el mismo formato
    private final ObjectMapper objectMapper;

    @SuppressWarnings ("nullness") // Para evitar el warning de nullness, ya que el método commence puede recibir parámetros nulos, pero en este caso no se van a utilizar.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

       LOGGER.error("Unauthorized error: {}", authException.getMessage());

        // F7: este 401 (sin token o token no válido) ocurre en el filtro de seguridad, ANTES de
        // llegar a ningún controlador, así que ControllerExceptionHandler no se entera. Antes
        // response.sendError(...) devolvía el cuerpo vacío; ahora se escribe el mismo ProblemDetail.
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Se requiere autenticación: falta el token JWT o no es válido");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
