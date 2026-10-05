package com.example.spring_security_jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import com.example.spring_security_jwt.entities.User;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

}
