package com.example.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.entities.Tag;
import com.example.entities.Tutorial;

/* Misma configuracion que TutorialRepositoryTest (ver alli la explicacion de ddl-auto=update).
 * Al ser identica, Spring reutiliza el mismo contexto para las dos clases. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TagRepositoryTest {

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private TutorialRepository tutorialRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Tutorial tutorial;
	private Tag tagJava;
	private Tag tagSpring;

	@BeforeEach
	void setUp() {

		tutorial = Tutorial.builder()
				.title("Curso de JPA [repo-test]")
				.description("Relaciones many-to-many")
				.published(true)
				.build();

		tagJava = Tag.builder().name("Java [repo-test]").build();
		tagSpring = Tag.builder().name("Spring [repo-test]").build();
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("Test para persistir un tag")
	void testSaveTag() {

		// when
		Tag tagGuardado = tagRepository.save(tagJava);

		// then
		assertThat(tagGuardado.getId()).isGreaterThan(0);
		assertThat(tagGuardado.getName()).isEqualTo("Java [repo-test]");
	}

	@Test
	@DisplayName("Test de la consulta many-to-many: tags de un tutorial")
	void testFindTagsByTutorialsId() {

		// given
		tutorial.addTag(tagJava);
		tutorial.addTag(tagSpring);
		Tutorial tutorialGuardado = tutorialRepository.save(tutorial);
		flushAndClear();

		// when
		List<Tag> tags = tagRepository.findTagsByTutorialsId(tutorialGuardado.getId());

		// then
		assertThat(tags)
				.extracting(Tag::getName)
				.containsExactlyInAnyOrder("Java [repo-test]", "Spring [repo-test]");
	}

	@Test
	@DisplayName("Test de la consulta many-to-many: un tutorial sin tags devuelve una lista vacia")
	void testFindTagsByTutorialsIdSinTags() {

		// given
		Tutorial tutorialGuardado = tutorialRepository.save(tutorial);
		flushAndClear();

		// when
		List<Tag> tags = tagRepository.findTagsByTutorialsId(tutorialGuardado.getId());

		// then
		assertThat(tags).isEmpty();
	}

	@Test
	@DisplayName("Borrar un tag asignado funciona si antes se quita de sus tutoriales (lo que hace TagServiceImpl.deleteTag)")
	void testDeleteTagAsignadoQuitandoloAntes() {

		// given
		tutorial.addTag(tagJava);
		tutorial.addTag(tagSpring);
		Tutorial tutorialGuardado = tutorialRepository.save(tutorial);
		flushAndClear();

		Tag tagAsignado = tagRepository.findById(tagJava.getId()).orElseThrow();

		// when: Tag es el lado inverso (mappedBy), hay que quitarlo de cada tutorial (lado propietario)
		for (Tutorial t : new ArrayList<>(tagAsignado.getTutorials())) {
			t.removeTag(tagAsignado.getId());
		}
		tagRepository.delete(tagAsignado);
		flushAndClear();

		// then: el tag ya no existe y el tutorial conserva solo el otro tag
		assertThat(tagRepository.findById(tagJava.getId())).isEmpty();
		assertThat(tagRepository.findTagsByTutorialsId(tutorialGuardado.getId()))
				.extracting(Tag::getName)
				.containsExactly("Spring [repo-test]");
	}
}
