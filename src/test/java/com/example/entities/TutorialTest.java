package com.example.entities;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* Test unitario PURO: no hay ninguna anotación de Spring, así que no se levanta
 * el contexto ni se toca la base de datos. Solo se prueba la lógica Java de la
 * entidad: que addTag() y removeTag() mantienen sincronizados los DOS lados de
 * la relación many-to-many (tutorial.tags y tag.tutorials). */
class TutorialTest {

	private Tutorial tutorial;
	private Tag tagJava;
	private Tag tagSpring;

	@BeforeEach
	void setUp() {

		tutorial = Tutorial.builder()
				.title("Spring Boot Tutorial")
				.description("Learn Spring Boot")
				.published(true)
				.build();

		/* Se les da id porque removeTag() busca el tag por su id */
		tagJava = Tag.builder().id(1L).name("Java").build();
		tagSpring = Tag.builder().id(2L).name("Spring Boot").build();
	}

	@Test
	@DisplayName("Un tutorial recién creado con el builder tiene el Set de tags vacío, no null")
	void testBuilderInicializaTags() {

		// then
		assertThat(tutorial.getTags()).isNotNull().isEmpty();
		assertThat(tagJava.getTutorials()).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("addTag() añade la relación por los dos lados")
	void testAddTag() {

		// when
		tutorial.addTag(tagJava);

		// then
		assertThat(tutorial.getTags()).containsExactly(tagJava);
		assertThat(tagJava.getTutorials()).containsExactly(tutorial);
	}

	@Test
	@DisplayName("addTag() con varios tags los añade todos")
	void testAddVariosTags() {

		// when
		tutorial.addTag(tagJava);
		tutorial.addTag(tagSpring);

		// then
		assertThat(tutorial.getTags()).containsExactlyInAnyOrder(tagJava, tagSpring);
		assertThat(tagJava.getTutorials()).containsExactly(tutorial);
		assertThat(tagSpring.getTutorials()).containsExactly(tutorial);
	}

	@Test
	@DisplayName("removeTag() quita la relación por los dos lados")
	void testRemoveTag() {

		// given
		tutorial.addTag(tagJava);
		tutorial.addTag(tagSpring);

		// when
		tutorial.removeTag(1L);

		// then
		assertThat(tutorial.getTags()).containsExactly(tagSpring);
		assertThat(tagJava.getTutorials()).isEmpty();
		assertThat(tagSpring.getTutorials()).containsExactly(tutorial);
	}

	@Test
	@DisplayName("removeTag() con un id que el tutorial no tiene no cambia nada ni lanza excepción")
	void testRemoveTagInexistente() {

		// given
		tutorial.addTag(tagJava);

		// when / then
		assertThatCode(() -> tutorial.removeTag(99L)).doesNotThrowAnyException();

		assertThat(tutorial.getTags()).containsExactly(tagJava);
		assertThat(tagJava.getTutorials()).containsExactly(tutorial);
	}
}
