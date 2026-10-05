package com.example.service;

import java.util.List;

import com.example.entities.Tag;
import com.example.entities.Tutorial;

public interface TagService {

    List<Tag> findAll();

    List<Tag> findTagsByTutorialsId(long tutorialId);

    Tag findById(long id);

    List<Tutorial> findTutorialsByTagsId(long tagId);

    Tag createTagWithTutorialId(long tutorialId, Tag tagRequest);

    Tag updateTag(long id, Tag tagRequest);

    void deleteTag(long id);

    void deleteTagFromTutorial(long tutorialId, long tagId);


}
