package com.example.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.entities.Tag;
import com.example.exception.BadRequestException;
import com.example.exception.ResourceNotFoundException;

/* F7: tests del formato de error comun (ProblemDetail, RFC 9457). Cada respuesta de error
 * debe tener Content-Type application/problem+json, el codigo HTTP correcto, los campos
 * status/title/detail/instance/timestamp y NO debe filtrar detalles internos.
 * Hereda de AbstractControllerTest, asi que comparte contexto con los otros tests de controlador. */
class ManejoErroresControllerTest extends AbstractControllerTest {

	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		adminToken = obtenerToken(ADMIN_EMAIL);
	}

	// ================= Formato comun =================

	@Test
	@DisplayName("F7: 404 de un recurso inexistente con todos los campos de ProblemDetail")
	void testFormatoProblemDetail() throws Exception {

		// given
		given(tutorialService.findById(99L))
				.willThrow(new ResourceNotFoundException("Not found Tutorial with id = 99"));

		// when / then
		mockMvc.perform(get("/api/tutorials/{id}", 99L)
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				/* "type" no aparece: Spring 7 lo omite cuando vale "about:blank", que segun el RFC 9457
				 * es el valor que se asume si falta */
				.andExpect(jsonPath("$.type").doesNotExist())
				.andExpect(jsonPath("$.title", is("Not Found")))
				.andExpect(jsonPath("$.status", is(404)))
				.andExpect(jsonPath("$.detail", is("Not found Tutorial with id = 99")))
				.andExpect(jsonPath("$.instance", is("/api/tutorials/99")))
				.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	@Test
	@DisplayName("F7: 500 con un mensaje generico que no filtra detalles internos")
	void testError500NoFiltraDetalles() throws Exception {

		// given: una excepcion inesperada con informacion interna en el mensaje
		given(tutorialService.findAll())
				.willThrow(new RuntimeException("SQL error en la tabla secreta tutorials_tags"));

		// when / then
		mockMvc.perform(get("/api/tutorials")
						.header("Authorization", adminToken))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status", is(500)))
				.andExpect(jsonPath("$.detail", not(containsString("secreta"))))
				.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	// ================= Errores del cliente que antes daban 500 =================

	@Test
	@DisplayName("F7: id no numerico -> 400 (antes 500)")
	void testIdNoNumerico() throws Exception {

		mockMvc.perform(get("/api/tutorials/{id}", "abc")
						.header("Authorization", adminToken))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("'id'")))
				.andExpect(jsonPath("$.detail", containsString("abc")))
				.andExpect(jsonPath("$.timestamp", notNullValue()));

		verifyNoInteractions(tutorialService);
	}

	@Test
	@DisplayName("F7: ruta que no existe -> 404 (antes 500)")
	void testRutaInexistente() throws Exception {

		mockMvc.perform(get("/api/no-existe")
						.header("Authorization", adminToken))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("/api/no-existe")));
	}

	@Test
	@DisplayName("F7: metodo HTTP no soportado -> 405 con la cabecera Allow (antes 500)")
	void testMetodoNoPermitido() throws Exception {

		mockMvc.perform(patch("/api/tutorials/{id}", 1L)
						.header("Authorization", adminToken))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().exists("Allow"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status", is(405)));
	}

	@Test
	@DisplayName("F7: JSON mal formado -> 400 (antes 500)")
	void testJsonMalFormado() throws Exception {

		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\": "))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("JSON")));

		verifyNoInteractions(tutorialService);
	}

	@Test
	@DisplayName("F7: peticion sin cuerpo -> 400 sin mostrar la firma interna del metodo (antes 500)")
	void testSinCuerpo() throws Exception {

		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", not(containsString("com.example"))));
	}

	@Test
	@DisplayName("F7: Content-Type no soportado -> 415 (antes 500)")
	void testContentTypeNoSoportado() throws Exception {

		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", adminToken)
						.contentType(MediaType.TEXT_PLAIN)
						.content("hola"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// ================= Validacion =================

	@Test
	@DisplayName("F7: crear un tutorial sin titulo -> 400 con el campo en 'errors' (antes 201)")
	void testTutorialSinTitulo() throws Exception {

		mockMvc.perform(post("/api/tutorials")
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.title", is("El título es obligatorio")))
				.andExpect(jsonPath("$.timestamp", notNullValue()));

		verify(tutorialService, never()).create(any());
	}

	@Test
	@DisplayName("F7: renombrar un tag con el nombre vacio -> 400")
	void testUpdateTagSinNombre() throws Exception {

		mockMvc.perform(put("/api/tags/{id}", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name", is("El nombre es obligatorio")));

		verify(tagService, never()).updateTag(eq(1L), any(Tag.class));
	}

	@Test
	@DisplayName("F7: el servicio rechaza un tag nuevo sin nombre (BadRequestException) -> 400")
	void testTagNuevoSinNombre() throws Exception {

		// given
		given(tagService.createTagWithTutorialId(eq(1L), any(Tag.class)))
				.willThrow(new BadRequestException("El nombre es obligatorio para crear un tag nuevo"));

		// when / then
		mockMvc.perform(post("/api/tutorials/{tutorialId}/tags", 1L)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", is("El nombre es obligatorio para crear un tag nuevo")));
	}

	// ================= Seguridad =================

	@Test
	@DisplayName("F7: sin token -> 401 con cuerpo ProblemDetail (antes el cuerpo estaba vacio)")
	void testSinToken() throws Exception {

		mockMvc.perform(get("/api/tutorials"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status", is(401)))
				.andExpect(jsonPath("$.title", is("Unauthorized")))
				.andExpect(jsonPath("$.detail", notNullValue()))
				.andExpect(jsonPath("$.instance", is("/api/tutorials")))
				.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	@Test
	@DisplayName("F7: un USER sin permiso -> 403 en formato ProblemDetail")
	void testForbidden() throws Exception {

		// given
		String userToken = obtenerToken(USER_EMAIL);

		// when / then
		mockMvc.perform(delete("/api/tags/{id}", 1L)
						.header("Authorization", userToken))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status", is(403)))
				.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	@Test
	@DisplayName("F7: login con contraseña incorrecta -> 401 (antes 500)")
	void testLoginPasswordIncorrecta() throws Exception {

		mockMvc.perform(post("/api/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"admin@example.com\", \"password\": \"incorrecta\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", is("Email o contraseña incorrectos")));
	}

	@Test
	@DisplayName("F7: login con un email que no existe -> el MISMO 401 (no revela qué emails están registrados)")
	void testLoginUsuarioInexistente() throws Exception {

		mockMvc.perform(post("/api/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"noexiste@example.com\", \"password\": \"incorrecta\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail", is("Email o contraseña incorrectos")));
	}

	@Test
	@DisplayName("F7: login con datos no validos -> 400 en el formato comun, con 'errors'")
	void testLoginValidacion() throws Exception {

		mockMvc.perform(post("/api/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"\", \"password\": \"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.email", notNullValue()))
				.andExpect(jsonPath("$.errors.password", notNullValue()));
	}

	@Test
	@DisplayName("F7: registro con un username ya usado -> 409 Conflict")
	void testSignupUsernameDuplicado() throws Exception {

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"admin\", \"email\": \"otro@example.com\", \"password\": \"Temp2026$$\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("username")));
	}

	@Test
	@DisplayName("F7: registro con un email ya usado -> 409 Conflict")
	void testSignupEmailDuplicado() throws Exception {

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"nuevo\", \"email\": \"admin@example.com\", \"password\": \"Temp2026$$\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("email")));
	}
}
