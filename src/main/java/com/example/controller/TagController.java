package com.example.controller;

import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.RestController;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.TagRepository;
import com.example.repository.TutorialRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagController {

    private final TutorialRepository tutorialRepository;
    private final TagRepository tagRepository;

    @GetMapping("/tags")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Tag>> getAllTags() {
        List<Tag> tags = new ArrayList<Tag>();

        tagRepository.findAll().forEach(tags::add);

        if (tags.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(tags, HttpStatus.OK);
    }

    @GetMapping("/tutorials/{tutorialId}/tags")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Tag>> getAllTagsByTutorialId(@PathVariable Long tutorialId) {
        if (!tutorialRepository.existsById(tutorialId)) {
            throw new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId);
        }

        List<Tag> tags = tagRepository.findTagsByTutorialsId(tutorialId);

        return new ResponseEntity<>(tags, HttpStatus.OK);
    }

    @GetMapping("/tags/{id}")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Tag> getTagsById(@PathVariable Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tag with id = " + id));

        return new ResponseEntity<>(tag, HttpStatus.OK);
    }

    @GetMapping("/tags/{tagId}/tutorials")
    @PreAuthorize ("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Tutorial>> getAllTutorialsByTagId(@PathVariable Long tagId) {
        if (!tagRepository.existsById(tagId)) {
            throw new ResourceNotFoundException("Not found Tag with id = " + tagId);
        }

        List<Tutorial> tutorials = tutorialRepository.findTutorialsByTagsId(tagId);
        return new ResponseEntity<>(tutorials, HttpStatus.OK);
    }

    @PostMapping("/tutorials/{tutorialId}/tags")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<Tag> addTag(@PathVariable Long tutorialId, @RequestBody Tag tagRequest) {

        Tag tag = tutorialRepository.findById(tutorialId).map(tutorial -> {

            long tagId = tagRequest.getId();

            // tag is existed
            if (tagId != 0L) {

                Tag _tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new ResourceNotFoundException("Not found Tag with id = " + tagId));
                tutorial.addTag(_tag);
                tutorialRepository.save(tutorial);
                return _tag;
            }

            // add and create new Tag
            tutorial.addTag(tagRequest);
            return tagRepository.save(tagRequest);
        }).orElseThrow(() -> new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId));

        return new ResponseEntity<>(tag, HttpStatus.CREATED);
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<Tag> updateTag(@PathVariable long id, @RequestBody Tag tagRequest) {

        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TagId " + id + "not found"));

        tag.setName(tagRequest.getName());

        return new ResponseEntity<>(tagRepository.save(tag), HttpStatus.OK);
    }

    @DeleteMapping("/tutorials/{tutorialId}/tags/{tagId}")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<HttpStatus> deleteTagFromTutorial(@PathVariable Long tutorialId,
            @PathVariable Long tagId) {

        Tutorial tutorial = tutorialRepository.findById(tutorialId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId));

        tutorial.removeTag(tagId);
        tutorialRepository.save(tutorial);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/tags/{id}")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<HttpStatus> deleteTag(@PathVariable long id) {
        
        tagRepository.deleteById(id);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
