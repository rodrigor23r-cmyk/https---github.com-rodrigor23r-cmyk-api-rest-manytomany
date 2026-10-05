package com.example.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.exception.BadRequestException;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.TagRepository;
import com.example.repository.TutorialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final TutorialRepository tutorialRepository;

    @Override
    @Transactional(readOnly = true) // Es más eficiente porque no comprueba si hay cambios que guardar al terminar
                                    // la transacción
    public List<Tag> findAll() {

        return tagRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true) // Es más eficiente porque no comprueba si hay cambios que guardar al terminar
                                    // la transacción
    public List<Tag> findTagsByTutorialsId(long tutorialId) {

        if (!tutorialRepository.existsById(tutorialId)) {
            throw new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId);
        }

        return tagRepository.findTagsByTutorialsId(tutorialId);
    }

    @Override
    @Transactional(readOnly = true)
    public Tag findById(long tagId) {

        return tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tag with id = " + tagId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tutorial> findTutorialsByTagsId(long tagId) {

        if (!tagRepository.existsById(tagId)) {
            throw new ResourceNotFoundException("Not found Tag with id = " + tagId);
        }

        List<Tutorial> tutorials = tutorialRepository.findTutorialsByTagsId(tagId);

        return tutorials;
    }

    @Override
    @Transactional
    public Tag createTagWithTutorialId(long tutorialId, Tag tagRequest) {

        // 1. El tutorial tiene que existir (si no, 404)
        Tutorial tutorial = tutorialRepository.findById(tutorialId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId));

        long tagId = tagRequest.getId();

        // 2a. El JSON trae id → el tag ya existe: se busca y se asocia
        if (tagId != 0L) {
            Tag tag = findById(tagId); // reutiliza tu findById (lanza 404 si no existe)
            tutorial.addTag(tag);
            tutorialRepository.save(tutorial);
            return tag;
        }

        // 2b. El JSON no trae id → tag nuevo: se asocia y se guarda
        // F7: un tag nuevo necesita nombre (no se puede usar @Valid, ver TagController.addTag)
        if (tagRequest.getName() == null || tagRequest.getName().isBlank()) {
            throw new BadRequestException("El nombre es obligatorio para crear un tag nuevo");
        }

        tutorial.addTag(tagRequest);
        return tagRepository.save(tagRequest);

    }

    @Override
    @Transactional
    public Tag updateTag(long id, Tag tagRequest) {

        Tag tag = this.findById(id);

        tag.setName(tagRequest.getName());
        return tagRepository.save(tag);
    }

    @Override
    @Transactional
    public void deleteTagFromTutorial(long tutorialId, long tagId) {

        findById(tagId); // lanza 404 si el tag no existe

        Tutorial tutorial = tutorialRepository.findById(tutorialId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tutorial with id = " + tutorialId));

        tutorial.removeTag(tagId);
        tutorialRepository.save(tutorial);

    }

    @Override
    @Transactional
    public void deleteTag(long id) {

        Tag tag = findById(id); // lanza 404 si el tag no existe

        /*
         * Tag es el lado inverso de la relación (mappedBy): borrarlo NO borra sus filas de
         * tutorials_tags y MySQL lo impide por la clave foránea. Hay que quitarlo antes de
         * cada tutorial (el lado propietario). Se recorre una COPIA porque removeTag() también
         * modifica tag.getTutorials() y modificar el Set que se está recorriendo lanzaría
         * ConcurrentModificationException.
         */
        for (Tutorial tutorial : new ArrayList<>(tag.getTutorials())) {
            tutorial.removeTag(id);
        }

        tagRepository.delete(tag);
    }
}