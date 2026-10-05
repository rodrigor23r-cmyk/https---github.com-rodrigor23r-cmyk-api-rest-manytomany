package com.example.spring_security_jwt.service;

import com.example.spring_security_jwt.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.spring_security_jwt.entities.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;

    @SuppressWarnings ("nullness") // Para evitar el warning de nullness, ya que el método loadUserByUsername puede devolver null
    //  si no se encuentra el usuario, pero en este caso se lanza una excepción en lugar de devolver null. 
    @Override
    @Transactional 
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        return UserDetailsImpl.build(user);
    }

}
