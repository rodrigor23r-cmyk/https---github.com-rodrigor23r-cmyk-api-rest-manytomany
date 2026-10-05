package com.example.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.TagRepository;
import com.example.repository.TutorialRepository;

/* Test unitario de TagServiceImpl. Necesita simular los DOS repositorios que usa. */
@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

	@Mock
	private TagRepository tagRepository;

	@Mock
	private TutorialRepository tutorialRepository;

	@InjectMocks
	private TagServiceImpl tagService;

	private Tutorial tutorial1;
	private Tutorial tutorial2;
	private Tag tagJava;
	private Tag tagSpring;

	@BeforeEach
	void setUp() {

		tutorial1 = Tutorial.builder().id(1L).title("Spring Boot Tutorial").published(true).build();
		tutorial2 = Tutorial.builder().id(2L).title("Java Tutorial").published(true).build();

		tagJava = Tag.builder().id(1L).name("Java").build();
		tagSpring = Tag.builder().id(2L).name("Spring Boot").build();
	}

	// ================= Consultas =================

	@Test
	@DisplayName("Test para recuperar todos los tags")
	void testFindAll() {

		// given
		given(tagRepository.findAll()).willReturn(List.of(tagJava, tagSpring));

		// when
		List<Tag> tags = tagService.findAll();

		// then
		assertThat(tags).containsExactly(tagJava, tagSpring);
	}

	@Test
	@DisplayName("Test para recuperar un tag por su id")
	void testFindById() {

		// given
		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));

		// when / then
		assertThat(tagService.findById(1L)).isSameAs(tagJava);
	}

	@Test
	@DisplayName("Test tag no encontrado: lanza ResourceNotFoundException")
	void testFindByIdNoEncontrado() {

		// given
		given(tagRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tag with id = 99");
	}

	@Test
	@DisplayName("Test para recuperar los tags de un tutorial")
	void testFindTagsByTutorialsId() {

		// given
		given(tutorialRepository.existsById(1L)).willReturn(true);
		given(tagRepository.findTagsByTutorialsId(1L)).willReturn(List.of(tagJava, tagSpring));

		// when
		List<Tag> tags = tagService.findTagsByTutorialsId(1L);

		// then
		assertThat(tags).containsExactly(tagJava, tagSpring);
	}

	@Test
	@DisplayName("Test tags de un tutorial que no existe: lanza excepcion y no consulta los tags")
	void testFindTagsByTutorialsIdTutorialNoExiste() {

		// given
		given(tutorialRepository.existsById(99L)).willReturn(false);

		// when / then
		assertThatThrownBy(() -> tagService.findTagsByTutorialsId(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tutorial with id = 99");

		verify(tagRepository, never()).findTagsByTutorialsId(any());
	}

	@Test
	@DisplayName("Test para recuperar los tutoriales de un tag")
	void testFindTutorialsByTagsId() {

		// given
		given(tagRepository.existsById(1L)).willReturn(true);
		given(tutorialRepository.findTutorialsByTagsId(1L)).willReturn(List.of(tutorial1, tutorial2));

		// when
		List<Tutorial> tutoriales = tagService.findTutorialsByTagsId(1L);

		// then
		assertThat(tutoriales).containsExactly(tutorial1, tutorial2);
	}

	@Test
	@DisplayName("Test tutoriales de un tag que no existe: lanza excepcion y no consulta los tutoriales")
	void testFindTutorialsByTagsIdTagNoExiste() {

		// given
		given(tagRepository.existsById(99L)).willReturn(false);

		// when / then
		assertThatThrownBy(() -> tagService.findTutorialsByTagsId(99L))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(tutorialRepository, never()).findTutorialsByTagsId(anyLong());
	}

	// ================= Añadir un tag a un tutorial =================

	@Test
	@DisplayName("Test añadir un tag NUEVO (sin id) a un tutorial: se asocia y se guarda")
	void testCreateTagNuevo() {

		// given
		Tag tagNuevo = Tag.builder().name("Docker").build(); // id = 0 -> tag nuevo

		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));
		given(tagRepository.save(tagNuevo)).willReturn(tagNuevo);

		// when
		Tag resultado = tagService.createTagWithTutorialId(1L, tagNuevo);

		// then
		assertThat(resultado.getName()).isEqualTo("Docker");
		assertThat(tutorial1.getTags()).containsExactly(tagNuevo);
		assertThat(tagNuevo.getTutorials()).containsExactly(tutorial1);
		verify(tagRepository).save(tagNuevo);
		verify(tagRepository, never()).findById(anyLong()); // no se busca: es nuevo
	}

	@Test
	@DisplayName("Test añadir un tag EXISTENTE (con id) a un tutorial: se busca y se asocia sin crear otro")
	void testCreateTagExistente() {

		// given: del JSON solo llega el id
		Tag request = Tag.builder().id(1L).build();

		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));
		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));

		// when
		Tag resultado = tagService.createTagWithTutorialId(1L, request);

		// then: devuelve el tag de la base de datos (con su nombre), no el del JSON
		assertThat(resultado).isSameAs(tagJava);
		assertThat(tutorial1.getTags()).containsExactly(tagJava);
		assertThat(tagJava.getTutorials()).containsExactly(tutorial1);
		verify(tutorialRepository).save(tutorial1);
		verify(tagRepository, never()).save(any());
	}

	@Test
	@DisplayName("Test añadir un tag con id que no existe: lanza excepcion y el tutorial no cambia")
	void testCreateTagExistenteNoEncontrado() {

		// given
		Tag request = Tag.builder().id(99L).build();

		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));
		given(tagRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.createTagWithTutorialId(1L, request))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tag with id = 99");

		assertThat(tutorial1.getTags()).isEmpty();
		verify(tutorialRepository, never()).save(any());
	}

	@Test
	@DisplayName("Test añadir un tag a un tutorial que no existe: lanza excepcion y no toca los tags")
	void testCreateTagTutorialNoEncontrado() {

		// given
		given(tutorialRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.createTagWithTutorialId(99L, tagJava))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tutorial with id = 99");

		verifyNoInteractions(tagRepository);
	}

	// ================= Actualizar =================

	@Test
	@DisplayName("Test para actualizar el nombre de un tag")
	void testUpdateTag() {

		// given
		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));
		given(tagRepository.save(tagJava)).willReturn(tagJava);

		// when
		Tag actualizado = tagService.updateTag(1L, Tag.builder().name("Java 25").build());

		// then
		assertThat(actualizado.getId()).isEqualTo(1L);
		assertThat(actualizado.getName()).isEqualTo("Java 25");
	}

	@Test
	@DisplayName("Test actualizar un tag que no existe: lanza excepcion y no guarda nada")
	void testUpdateTagNoEncontrado() {

		// given
		given(tagRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.updateTag(99L, tagJava))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(tagRepository, never()).save(any());
	}

	// ================= Quitar un tag de un tutorial =================

	@Test
	@DisplayName("Test para quitar un tag de un tutorial")
	void testDeleteTagFromTutorial() {

		// given
		tutorial1.addTag(tagJava);
		tutorial1.addTag(tagSpring);

		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));
		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));

		// when
		tagService.deleteTagFromTutorial(1L, 1L);

		// then: se quita por los dos lados, el otro tag sigue
		assertThat(tutorial1.getTags()).containsExactly(tagSpring);
		assertThat(tagJava.getTutorials()).isEmpty();
		verify(tutorialRepository).save(tutorial1);
	}

	@Test
	@DisplayName("Test quitar un tag que no existe: lanza excepcion sin buscar el tutorial")
	void testDeleteTagFromTutorialTagNoExiste() {

		// given
		given(tagRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.deleteTagFromTutorial(1L, 99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tag with id = 99");

		verifyNoInteractions(tutorialRepository);
	}

	@Test
	@DisplayName("Test quitar un tag de un tutorial que no existe: lanza excepcion")
	void testDeleteTagFromTutorialTutorialNoExiste() {

		// given
		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));
		given(tutorialRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.deleteTagFromTutorial(99L, 1L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tutorial with id = 99");

		verify(tutorialRepository, never()).save(any());
	}

	// ================= Borrar un tag =================

	@Test
	@DisplayName("Test borrar un tag ASIGNADO: se quita de todos sus tutoriales y luego se borra")
	void testDeleteTagAsignado() {

		// given: tagJava esta en los dos tutoriales; tutorial1 tiene ademas tagSpring
		tutorial1.addTag(tagJava);
		tutorial1.addTag(tagSpring);
		tutorial2.addTag(tagJava);

		given(tagRepository.findById(1L)).willReturn(Optional.of(tagJava));

		// when
		tagService.deleteTag(1L);

		// then: ningun tutorial conserva el tag borrado (si no, MySQL fallaria por la clave foranea)
		assertThat(tutorial1.getTags()).containsExactly(tagSpring);
		assertThat(tutorial2.getTags()).isEmpty();
		assertThat(tagJava.getTutorials()).isEmpty();
		verify(tagRepository).delete(tagJava);
	}

	@Test
	@DisplayName("Test borrar un tag SIN tutoriales: se borra directamente")
	void testDeleteTagSinTutoriales() {

		// given
		given(tagRepository.findById(2L)).willReturn(Optional.of(tagSpring));

		// when
		tagService.deleteTag(2L);

		// then
		verify(tagRepository).delete(tagSpring);
	}

	@Test
	@DisplayName("Test borrar un tag que no existe: lanza excepcion y no borra nada")
	void testDeleteTagNoEncontrado() {

		// given
		given(tagRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tagService.deleteTag(99L))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(tagRepository, never()).delete(any());
	}
}
