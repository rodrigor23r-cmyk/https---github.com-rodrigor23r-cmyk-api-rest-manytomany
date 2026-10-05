package com.example;

import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.repository.TutorialRepository;
import com.example.spring_security_jwt.entities.ERole;
import com.example.spring_security_jwt.entities.Role;
import com.example.spring_security_jwt.entities.User;
import com.example.spring_security_jwt.repository.RoleRepository;
import com.example.spring_security_jwt.repository.UserRepository;

@Configuration 
public class CreateSampleData {

    @SuppressWarnings ("nullable")
    @Bean
    CommandLineRunner samplesData(TutorialRepository tutorialRepository, RoleRepository roleRepository
        , UserRepository userRepository, PasswordEncoder encoder) {
            
            
        return args -> {

            // Crear tags y tutoriales de ejemplo
            Tag javaTag = Tag.builder().name("Java").build();
            Tag springBootTag = Tag.builder().name("Spring Boot").build();

            Tutorial springBootTutorial = Tutorial.builder()
                .title("The Best Spring Boot Tutorial").description("Learn Spring Boot without pain").published(true).build();
            Tutorial javaTutorial = Tutorial.builder()
                .title("The Amazing Java Tutorial").description("Learn Java from scratch").published(false).build();

            // relacionar tutoriales con tags (addTag sincroniza los dos lados de la relación)
            springBootTutorial.addTag(springBootTag);
            springBootTutorial.addTag(javaTag);
            javaTutorial.addTag(javaTag);

            // una sola transacción: el cascade PERSIST guarda también los tags
            tutorialRepository.saveAll(List.of(springBootTutorial, javaTutorial));

            // Crearemos los roles de usuario y administrador
            Role userRole = roleRepository.save(Role.builder()
                .name(ERole.ROLE_USER)
                .build()); 

            Role adminRole = roleRepository.save(Role.builder()
                .name(ERole.ROLE_ADMIN)
                .build());


            // Crearemos un usuario administrador y un usuario normal
            userRepository.save(User.builder()
                .username("admin")
                .email("admin@example.com")
                .password(encoder.encode("Temp2026$$"))
                .roles(Set.of(adminRole))
                .build());

            userRepository.save(User.builder()
                .username("user")
                .email("user@example.com")
                .password(encoder.encode("Temp2026$$"))
                .roles(Set.of(userRole))
                .build());
        };
    
    }
}
