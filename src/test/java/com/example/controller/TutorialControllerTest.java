package com.example.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

/* Tests de integracion de TutorialController: peticiones HTTP con MockMvc, pasando por
 * Spring Security (JWT + @PreAuthorize), con TutorialService simulado.
 * Si un test falla y quieres ver la peticion y la respuesta completas, añade
 * .andDo(print()) (import static ...MockMvcResultHandlers.print) como en la referencia. */
class TutorialControllerTest extends AbstractControllerTest {

	private String adminToken;

	private Tutorial tutorial1;
	private Tutorial tutorial2;

	@BeforeEach
	void setUp() throws Exception {

		adminToken = obtenerToken(ADMIN_EMAIL);

		tutorial1 = Tutorial.builder()
				.id(1L)
				.title("Spring Boot Tutorial")
				.description("Learn Spring Boot")
				.published(true)
				.build();
		tutorial1.addTag(Tag.builder().id(1L).name("Spring Boot").build());

		tutorial2 = Tutorial.builder()
				.id(2L)
				.title("Java Tutorial")
				.description("Learn Java")
				.published(false)
				.build();
	}

	// ================= GET =================

	@Test
	@DisplayName("Controller Test que recupera todos los tutoriales")
	void testFindAll() throws Exception {

		// given
		given(tutorialService.findAll()).willReturn(List.of(tutorial1, tutorial2));

		// when / then
		mockMvc.perform(get("/api/tutorials")
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(2)))
				.andExpect(jsonPath("$[0].title", is("Spring Boot Tutorial")))
				.andExpect(jsonPath("$[1].title", is("Java Tutorial")));
	}

	@Test
	@DisplayName("Controller Test sin tutoriales: 204 No Content")
	void testFindAllVacio() throws Exception {

		// given
		given(tutorialService.findAll()).willReturn(Collections.emptyList());

		// when / then
		mockMvc.perform(get("/api/tutorials")
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Controller Test que busca por titulo con ?title= (y no llama a findAll)")
	void testFindByTitle() throws Exception {

		// given
		given(tutorialService.findByTitleContaining("Spring")).willReturn(List.of(tutorial1));

		// when / then
		mockMvc.perform(get("/api/tutorials")
						.param("title", "Spring")
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(1)))
				.andExpect(jsonPath("$[0].title", is("Spring Boot Tutorial")));

		verify(tutorialService, never()).findAll();
	}

	@Test
	@DisplayName("Controller Test que recupera un tutorial por su id, con sus tags en el JSON")
	void testFindById() throws Exception {

		// given
		given(tutorialService.findById(1L)).willReturn(tutorial1);

		// when / then
		mockMvc.perform(get("/api/tutorials/{id}", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.title", is("Spring Boot Tutorial")))
				.andExpect(jsonPath("$.published", is(true)))
				.andExpect(jsonPath("$.tags[0].name", is("Spring Boot")))
				/* Tag.tutorials tiene @JsonIgnore: si no, el JSON seria infinito */
				.andExpect(jsonPath("$.tags[0].tutorials").doesNotExist());
	}

	@Test
	@DisplayName("Controller Test tutorial no encontrado: 404 con el mensaje de error")
	void testFindByIdNoEncontrado() throws Exception {

		// given: el servicio lanza la excepcion y ControllerExceptionHandler la convierte en 404
		given(tutorialService.findById(99L))
				.willThrow(new ResourceNotFoundException("Not found Tutorial with id = 99"));

		// when / then
		mockMvc.perform(get("/api/tutorials/{id}", 99L)
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.statusCode", is(404)))
				.andExpect(jsonPath("$.message", is("Not found Tutorial with id = 99")));
	}

	@Test
	@DisplayName("Controller Test que recupera los tutoriales publicados")
	void testFindByPublished() throws Exception {

		// given
		given(tutorialService.findByPublished(true)).willReturn(List.of(tutorial1));

		// when / then
		mockMvc.perform(get("/api/tutorials/published")
						.header("Authorization", adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size()", is(1)))
				.andExpect(jsonPath("$[0].published", is(true)));
	}

	// ================= POST / PUT / DELETE =================

	@Test
	@DisplayName("Controller Test para persistir un tutorial")
	void testCreate() throws Exception {

		// given
		given(tutorialService.create(any(Tutorial.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// when / then: a diferencia de la referencia no es multipart, es un JSON normal
		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tutorial2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title", is("Java Tutorial")))
				.andExpect(jsonPath("$.description", is("Learn Java")));
	}

	@Test
	@DisplayName("Controller Test que actualiza un tutorial")
	void testUpdate() throws Exception {

		// given
		Tutorial cambios = Tutorial.builder()
				.title("Titulo editado")
				.description("Descripcion editada")
				.published(false)
				.build();

		given(tutorialService.update(eq(1L), any(Tutorial.class)))
				.willAnswer(invocation -> invocation.getArgument(1));

		// when / then
		mockMvc.perform(put("/api/tutorials/{id}", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(cambios)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title", is("Titulo editado")))
				.andExpect(jsonPath("$.published", is(false)));
	}

	@Test
	@DisplayName("Controller Test actualizar un tutorial que no existe: 404")
	void testUpdateNoEncontrado() throws Exception {

		// given
		given(tutorialService.update(eq(99L), any(Tutorial.class)))
				.willThrow(new ResourceNotFoundException("Not found Tutorial with id = 99"));

		// when / then
		mockMvc.perform(put("/api/tutorials/{id}", 99L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tutorial2)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Controller Test que elimina un tutorial")
	void testDelete() throws Exception {

		// when / then
		mockMvc.perform(delete("/api/tutorials/{id}", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());

		verify(tutorialService).deleteById(1L);
	}

	@Test
	@DisplayName("Controller Test que elimina todos los tutoriales")
	void testDeleteAll() throws Exception {

		// when / then
		mockMvc.perform(delete("/api/tutorials")
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());

		verify(tutorialService).deleteAll();
	}

	// ================= Seguridad =================

	@Test
	@DisplayName("Seguridad: sin token -> 401 y el servicio no se llama")
	void testSinToken() throws Exception {

		mockMvc.perform(get("/api/tutorials"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(tutorialService);
	}

	@Test
	@DisplayName("Seguridad: token falso -> 401")
	void testTokenInvalido() throws Exception {

		mockMvc.perform(get("/api/tutorials")
						.header("Authorization", "Bearer esto.no-es.un-jwt"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(tutorialService);
	}

	@Test
	@DisplayName("Seguridad: un USER puede leer tutoriales")
	void testUserPuedeLeer() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);
		given(tutorialService.findAll()).willReturn(List.of(tutorial1));

		// when / then
		mockMvc.perform(get("/api/tutorials")
						.header("Authorization", userToken))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Seguridad: un USER no puede crear tutoriales -> 403 y el servicio no se llama")
	void testUserNoPuedeCrear() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);

		// when / then
		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tutorial2)))
				.andExpect(status().isForbidden());

		verify(tutorialService, never()).create(any());
	}

	@Test
	@DisplayName("Seguridad: un USER no puede borrar tutoriales -> 403")
	void testUserNoPuedeBorrar() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);

		// when / then
		mockMvc.perform(delete("/api/tutorials/{id}", 1L)
						.header("Authorization", userToken))
				.andExpect(status().isForbidden());

		verify(tutorialService, never()).deleteById(1L);
	}
}
