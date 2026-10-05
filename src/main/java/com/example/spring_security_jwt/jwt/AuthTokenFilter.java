package com.example.spring_security_jwt.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.spring_security_jwt.service.UserDetailsServiceImpl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
/*La función de esta clase será validar la información del token y si esto es exitoso,
establecerá la autenticación de un usuario en la solicitud o en el contexto de seguridad
de nuestra aplicación*/
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor 
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthTokenFilter.class);

    @SuppressWarnings ("nullable") // Para evitar el warning de nullability, ya que el método doFilterInternal puede recibir parámetros nulos, pero en este caso no se espera que sean nulos.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {

                String email = jwtUtils.getEmailFromJwtToken(jwt);

                UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authtentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, userDetails.getAuthorities());
                        
                authtentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authtentication);

            }
        } catch (Exception e) {
            // TODO: handle exception
            LOGGER.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {

        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer")) {

            return headerAuth.substring(7);

        }

        return null;

    }
}
