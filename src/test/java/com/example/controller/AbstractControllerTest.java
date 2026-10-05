package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.service.TagService;
import com.example.service.TutorialService;
import com.example.spring_security_jwt.payload.request.LoginRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * Clase base de los tests de integracion de los controladores.
 *
 * Por que una clase base: Spring guarda en cache un contexto por cada configuracion
 * de test distinta (anotaciones + @MockitoBean). Si TutorialControllerTest y
 * TagControllerTest tuvieran configuraciones diferentes, se arrancarian DOS contextos
 * y cada uno, con create-drop, borraria y recrearia las tablas de MySQL. Heredando
 * todos de esta clase, la configuracion es identica y se comparte un unico contexto.
 *
 * @SpringBootTest levanta la aplicacion completa, incluida Spring Security (por eso
 * no sirve @WebMvcTest, igual que en el proyecto de referencia). Al arrancar se ejecuta
 * CreateSampleData, que crea los usuarios admin@example.com y user@example.com con los
 * que se hace login de verdad para obtener un JWT.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@AutoConfigureMockMvc
abstract class AbstractControllerTest {

	protected static final String PASSWORD = "Temp2026$$";
	protected static final String ADMIN_EMAIL = "admin@example.com";
	protected static final String USER_EMAIL = "user@example.com";

	/* Para hacer peticiones HTTP a los endpoints sin arrancar un servidor real */
	@Autowired
	protected MockMvc mockMvc;

	/* Jackson 3 (Spring Boot 4): paquete tools.jackson, no com.fasterxml.jackson */
	@Autowired
	protected ObjectMapper objectMapper;

	/* Los servicios se simulan: los tests de controlador solo prueban la capa HTTP
	 * (rutas, codigos de estado, JSON y seguridad). La logica ya se prueba en los
	 * tests de servicio con Mockito. */
	@MockitoBean
	protected TutorialService tutorialService;

	@MockitoBean
	protected TagService tagService;

	/**
	 * Hace login en /api/auth/signin y devuelve la cabecera Authorization lista para usar.
	 * En este proyecto el login es por EMAIL (en la referencia era por username).
	 */
	protected String obtenerToken(String email) throws Exception {

		LoginRequest loginRequest = LoginRequest.builder()
				.email(email)
				.password(PASSWORD)
				.build();

		String respuesta = mockMvc.perform(post("/api/auth/signin")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return "Bearer " + new JSONObject(respuesta).getString("token");
	}
}
