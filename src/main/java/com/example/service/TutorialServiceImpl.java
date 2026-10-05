package com.example.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.entities.Tutorial;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.TutorialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorialServiceImpl implements TutorialService {

    // Hay que inyectar, por constructor, la dependencia de TutorialRepository
    private final TutorialRepository tutorialRepository;

    /*
     * readOnly = true: las consultas no modifican datos, así Hibernate no comprueba
     * si hay cambios que guardar al terminar la transacción (es más eficiente).
     * Se usa el @Transactional de Spring (no el de jakarta) porque es el que admite readOnly.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tutorial> findAll() {
        return tutorialRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tutorial> findByTitleContaining(String title) {
        return tutorialRepository.findByTitleContaining(title);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tutorial> findByPublished(boolean published) {
        return tutorialRepository.findByPublished(published);
    }

    @Override
    @Transactional(readOnly = true)
    public Tutorial findById(long id) {
        // La excepción la convierte en un 404 el ControllerExceptionHandler
        return tutorialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found Tutorial with id = " + id));
    }

    @Override
    @Transactional
    public Tutorial create(Tutorial tutorial) {
        // Solo se copian título y descripción: se ignora el id y los tags que vengan en el JSON
        return tutorialRepository.save(
                Tutorial.builder()
                        .title(tutorial.getTitle())
                        .description(tutorial.getDescription())
                        .published(true)
                        .build());
    }

    @Override
    @Transactional
    public Tutorial update(long id, Tutorial tutorial) {
        Tutorial _tutorial = findById(id);

        _tutorial.setTitle(tutorial.getTitle());
        _tutorial.setDescription(tutorial.getDescription());
        _tutorial.setPublished(tutorial.isPublished());

        return tutorialRepository.save(_tutorial);
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        tutorialRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAll() {
        tutorialRepository.deleteAll();
    }
}
