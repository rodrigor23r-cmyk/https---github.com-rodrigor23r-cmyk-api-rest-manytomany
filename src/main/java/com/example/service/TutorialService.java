package com.example.service;

import java.util.List;

import com.example.entities.Tutorial;

public interface TutorialService {

    List<Tutorial> findAll();

    List<Tutorial> findByTitleContaining(String title);

    List<Tutorial> findByPublished(boolean published);

    /** Lanza ResourceNotFoundException si no existe */
    Tutorial findById(long id);

    Tutorial create(Tutorial tutorial);

    /** Lanza ResourceNotFoundException si no existe */
    Tutorial update(long id, Tutorial tutorial);

    void deleteById(long id);

    void deleteAll();
}
