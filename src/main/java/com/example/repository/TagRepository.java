package com.example.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entities.Tag;
import java.util.List;


public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findTagsByTutorialsId(Long tutorialId);

}
