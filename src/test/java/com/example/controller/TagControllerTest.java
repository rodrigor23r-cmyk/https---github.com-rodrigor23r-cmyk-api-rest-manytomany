package com.example.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.exception.ResourceNotFoundException;

/* Tests de integracion de TagController. Hereda la misma configuracion que
 * TutorialControllerTest, asi que comparten el mismo contexto de Spring. */
class TagControllerTest extends AbstractControllerTest {

	private String adminToken;

	private Tag tagJava;
	private Tag tagSpring;
	private Tutorial tutorial1;

	@BeforeEach
	void setUp() throws Exception {

		adminToken = obtenerToken(ADMIN_EMAIL);

		tagJava = Tag.builder().id(1L).name("Java").build();
		tagSpring = Tag.builder().id(2L).name("Spring Boot").build();

		tutorial1 = Tutorial.builder()
				.id(1L)
				.title("Spring Boot Tutorial")
				.description("Learn Spring Boot")
				.published(true)
				.build();
	}

	// ================= GET =================

	@Test
	@DisplayName("Controller Test que recupera todos los tags")
	void testFindAll() throws Exception {

		// given
		given(tagService.findAll()).willReturn(List.of(tagJava, tagSpring));

		// when / then
		mockMvc.perform(get("/api/tags")
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(2)))
				.andExpect(jsonPath("$[0].name", is("Java")))
				.andExpect(jsonPath("$[0].tutorials").doesNotExist());
	}

	@Test
	@DisplayName("Controller Test sin tags: 204 No Content")
	void testFindAllVacio() throws Exception {

		// given
		given(tagService.findAll()).willReturn(Collections.emptyList());

		// when / then
		mockMvc.perform(get("/api/tags")
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Controller Test que recupera un tag por su id")
	void testFindById() throws Exception {

		// given
		given(tagService.findById(1L)).willReturn(tagJava);

		// when / then
		mockMvc.perform(get("/api/tags/{id}", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.name", is("Java")));
	}

	@Test
	@DisplayName("Controller Test tag no encontrado: 404")
	void testFindByIdNoEncontrado() throws Exception {

		// given
		given(tagService.findById(99L))
				.willThrow(new ResourceNotFoundException("Not found Tag with id = 99"));

		// when / then
		mockMvc.perform(get("/api/tags/{id}", 99L)
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound())
				// F7: formato ProblemDetail ($.message -> $.detail)
				.andExpect(jsonPath("$.detail", is("Not found Tag with id = 99")));
	}

	@Test
	@DisplayName("Controller Test que recupera los tags de un tutorial")
	void testFindTagsByTutorialId() throws Exception {

		// given
		given(tagService.findTagsByTutorialsId(1L)).willReturn(List.of(tagJava, tagSpring));

		// when / then
		mockMvc.perform(get("/api/tutorials/{tutorialId}/tags", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(2)));
	}

	@Test
	@DisplayName("Controller Test tags de un tutorial que no existe: 404")
	void testFindTagsByTutorialIdNoEncontrado() throws Exception {

		// given
		given(tagService.findTagsByTutorialsId(99L))
				.willThrow(new ResourceNotFoundException("Not found Tutorial with id = 99"));

		// when / then
		mockMvc.perform(get("/api/tutorials/{tutorialId}/tags", 99L)
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound())
				// F7: formato ProblemDetail ($.message -> $.detail)
				.andExpect(jsonPath("$.detail", is("Not found Tutorial with id = 99")));
	}

	@Test
	@DisplayName("Controller Test que recupera los tutoriales de un tag")
	void testFindTutorialsByTagId() throws Exception {

		// given
		given(tagService.findTutorialsByTagsId(1L)).willReturn(List.of(tutorial1));

		// when / then
		mockMvc.perform(get("/api/tags/{tagId}/tutorials", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(1)))
				.andExpect(jsonPath("$[0].title", is("Spring Boot Tutorial")));
	}

	// ================= POST / PUT / DELETE =================

	@Test
	@DisplayName("Controller Test que añade un tag NUEVO a un tutorial")
	void testAddTagNuevo() throws Exception {

		// given: el servicio devuelve el tag ya guardado (con id)
		given(tagService.createTagWithTutorialId(eq(1L), any(Tag.class))).willReturn(tagJava);

		// when / then
		mockMvc.perform(post("/api/tutorials/{tutorialId}/tags", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Java\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.name", is("Java")));

		/* argThat comprueba lo que el controlador paso al servicio: el JSON convertido en Tag */
		verify(tagService).createTagWithTutorialId(eq(1L),
				argThat(tag -> tag.getId() == 0L && tag.getName().equals("Java")));
	}

	@Test
	@DisplayName("Controller Test que añade un tag EXISTENTE (solo id en el JSON) a un tutorial")
	void testAddTagExistente() throws Exception {

		// given
		given(tagService.createTagWithTutorialId(eq(1L), any(Tag.class))).willReturn(tagJava);

		// when / then
		mockMvc.perform(post("/api/tutorials/{tutorialId}/tags", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": 1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("Java")));

		verify(tagService).createTagWithTutorialId(eq(1L), argThat(tag -> tag.getId() == 1L));
	}

	@Test
	@DisplayName("Controller Test añadir un tag a un tutorial que no existe: 404")
	void testAddTagTutorialNoEncontrado() throws Exception {

		// given
		given(tagService.createTagWithTutorialId(eq(99L), any(Tag.class)))
				.willThrow(new ResourceNotFoundException("Not found Tutorial with id = 99"));

		// when / then
		mockMvc.perform(post("/api/tutorials/{tutorialId}/tags", 99L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Java\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Controller Test que actualiza un tag")
	void testUpdateTag() throws Exception {

		// given
		given(tagService.updateTag(eq(1L), any(Tag.class)))
				.willReturn(Tag.builder().id(1L).name("Java 25").build());

		// when / then
		mockMvc.perform(put("/api/tags/{id}", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Java 25\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Java 25")));
	}

	@Test
	@DisplayName("Controller Test que quita un tag de un tutorial")
	void testDeleteTagFromTutorial() throws Exception {

		// when / then
		mockMvc.perform(delete("/api/tutorials/{tutorialId}/tags/{tagId}", 1L, 2L)
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());

		verify(tagService).deleteTagFromTutorial(1L, 2L);
	}

	@Test
	@DisplayName("Controller Test que elimina un tag")
	void testDeleteTag() throws Exception {

		// when / then
		mockMvc.perform(delete("/api/tags/{id}", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());

		verify(tagService).deleteTag(1L);
	}

	@Test
	@DisplayName("Controller Test eliminar un tag que no existe: 404")
	void testDeleteTagNoEncontrado() throws Exception {

		// given: para metodos void se usa willThrow(...).given(mock).metodo(...)
		willThrow(new ResourceNotFoundException("Not found Tag with id = 99"))
				.given(tagService).deleteTag(99L);

		// when / then
		mockMvc.perform(delete("/api/tags/{id}", 99L)
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound());
	}

	// ================= Seguridad =================

	@Test
	@DisplayName("Seguridad: un USER no puede eliminar tags -> 403 y el servicio no se llama")
	void testUserNoPuedeBorrarTag() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);

		// when / then
		mockMvc.perform(delete("/api/tags/{id}", 1L)
						.header("Authorization", userToken))
				.andExpect(status().isForbidden());

		verify(tagService, never()).deleteTag(anyLong());
	}

	@Test
	@DisplayName("Seguridad: un USER no puede añadir tags a un tutorial -> 403")
	void testUserNoPuedeAñadirTag() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);

		// when / then
		mockMvc.perform(post("/api/tutorials/{tutorialId}/tags", 1L)
						.header("Authorization", userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Java\"}"))
				.andExpect(status().isForbidden());

		verify(tagService, never()).createTagWithTutorialId(anyLong(), any());
	}
}
