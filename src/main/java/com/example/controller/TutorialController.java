package com.example.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.entities.Tutorial;
import com.example.service.TutorialService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TutorialController {
    private final TutorialService tutorialService;

    @GetMapping("/tutorials")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Tutorial>> getAllTutorials(@RequestParam(required = false) String title) {

        List<Tutorial> tutorials;

        if (title == null)
            tutorials = tutorialService.findAll();
        else
            tutorials = tutorialService.findByTitleContaining(title);

        if (tutorials.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(tutorials, HttpStatus.OK);
    }

    @GetMapping("/tutorials/{id}")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Tutorial> getTutorialById(@PathVariable("id") long id) {

        return new ResponseEntity<>(tutorialService.findById(id), HttpStatus.OK);
    }

    @PostMapping("/tutorials")
    @PreAuthorize ("hasRole('ADMIN')")
    // F7: @Valid aplica las anotaciones de validación de Tutorial (@NotBlank en title) -> 400 si fallan
    public ResponseEntity<Tutorial> createTutorial(@Valid @RequestBody Tutorial tutorial) {

        return new ResponseEntity<>(tutorialService.create(tutorial), HttpStatus.CREATED);
    }

    @PutMapping("/tutorials/{id}")
    @PreAuthorize ("hasRole('ADMIN')")
    // F7: @Valid, igual que en createTutorial
    public ResponseEntity<Tutorial> updateTutorial(@PathVariable("id") long id, @Valid @RequestBody Tutorial tutorial) {

        return new ResponseEntity<>(tutorialService.update(id, tutorial), HttpStatus.OK);
    }

    @DeleteMapping("/tutorials/{id}")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<HttpStatus> deleteTutorial(@PathVariable("id") long id) {
        tutorialService.deleteById(id);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/tutorials")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<HttpStatus> deleteAllTutorials() {
        tutorialService.deleteAll();

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/tutorials/published")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Tutorial>> findByPublished() {
        List<Tutorial> tutorials = tutorialService.findByPublished(true);

        if (tutorials.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(tutorials, HttpStatus.OK);

    }
}
