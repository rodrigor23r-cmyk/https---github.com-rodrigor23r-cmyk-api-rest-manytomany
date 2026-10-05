package com.example.spring_security_jwt.service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.spring_security_jwt.entities.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;


/**
 * UserDetailsImpl
 */
@Data 
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {

    // private static final long serialVersionUID = 1L; 
    // No es necesario serializar la clase, ya que no se va a enviar a través de la red ni se va a guardar en disco. 
    // Además UserDetailsImpl no implementa Serializable, por lo que no se puede serializar. Por lo tanto, no es necesario definir un serialVersionUID.
    private long id;
    private String username;
    private String email;

    @JsonIgnore // Para que no se muestre la contraseña en las respuestas JSON
    private String password;

    private Collection<? extends GrantedAuthority> authorities;

    public static UserDetails build(User user) {

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
        return new UserDetailsImpl(user.getId(), user.getUsername(), user.getEmail(), user.getPassword(), authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        
        return password;
    }

    @Override
    public String getUsername() {
        
        return username;
    }


    
}
