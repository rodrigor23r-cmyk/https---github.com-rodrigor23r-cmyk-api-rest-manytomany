package com.example.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.entities.Tag;
import com.example.entities.Tutorial;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.TutorialRepository;

/* Test unitario de la capa de servicio. El repositorio NO es real: se simula (mock)
 * con Mockito para aislar la logica del servicio. No se levanta Spring ni se toca MySQL. */
@ExtendWith(MockitoExtension.class)
class TutorialServiceImplTest {

	/* La dependencia @Mock simula el repositorio en lugar de inyectarlo realmente */
	@Mock
	private TutorialRepository tutorialRepository;

	/* Crea un TutorialServiceImpl real y le inyecta los @Mock por el constructor */
	@InjectMocks
	private TutorialServiceImpl tutorialService;

	private Tutorial tutorial1;
	private Tutorial tutorial2;

	@BeforeEach
	void setUp() {

		tutorial1 = Tutorial.builder()
				.id(1L)
				.title("Spring Boot Tutorial")
				.description("Learn Spring Boot")
				.published(true)
				.build();

		tutorial2 = Tutorial.builder()
				.id(2L)
				.title("Java Tutorial")
				.description("Learn Java")
				.published(false)
				.build();
	}

	@Test
	@DisplayName("Test para recuperar todos los tutoriales")
	void testFindAll() {

		// given
		given(tutorialRepository.findAll()).willReturn(List.of(tutorial1, tutorial2));

		// when
		List<Tutorial> tutoriales = tutorialService.findAll();

		// then
		assertThat(tutoriales).containsExactly(tutorial1, tutorial2);
	}

	@Test
	@DisplayName("Test para recuperar una lista vacia de tutoriales")
	void testFindAllVacio() {

		// given
		given(tutorialRepository.findAll()).willReturn(Collections.emptyList());

		// when
		List<Tutorial> tutoriales = tutorialService.findAll();

		// then
		assertThat(tutoriales).isEmpty();
	}

	@Test
	@DisplayName("Test para buscar tutoriales por un trozo del titulo")
	void testFindByTitleContaining() {

		// given
		given(tutorialRepository.findByTitleContaining("Spring")).willReturn(List.of(tutorial1));

		// when
		List<Tutorial> tutoriales = tutorialService.findByTitleContaining("Spring");

		// then
		assertThat(tutoriales).containsExactly(tutorial1);
	}

	@Test
	@DisplayName("Test para recuperar los tutoriales publicados")
	void testFindByPublished() {

		// given
		given(tutorialRepository.findByPublished(true)).willReturn(List.of(tutorial1));

		// when
		List<Tutorial> tutoriales = tutorialService.findByPublished(true);

		// then
		assertThat(tutoriales).containsExactly(tutorial1);
	}

	@Test
	@DisplayName("Test para recuperar un tutorial por su id")
	void testFindById() {

		// given
		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));

		// when
		Tutorial encontrado = tutorialService.findById(1L);

		// then
		assertThat(encontrado).isSameAs(tutorial1);
	}

	@Test
	@DisplayName("Test tutorial no encontrado: lanza ResourceNotFoundException")
	void testFindByIdNoEncontrado() {

		// given
		given(tutorialRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tutorialService.findById(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Not found Tutorial with id = 99");
	}

	@Test
	@DisplayName("Test para crear un tutorial: siempre publicado e ignorando id y tags del JSON")
	void testCreate() {
		// 1. GIVEN (Dado un escenario inicial)
		// given: lo que llega del JSON trae id, published=false y un tag
		Tutorial request = Tutorial.builder()
				.id(55L)
				.title("Nuevo")
				.description("Descripcion")
				.published(false)
				.build();
		request.addTag(Tag.builder().id(7L).name("Java").build());
		// Simulamos el comportamiento del repositorio falso:
		/* willAnswer devuelve el mismo objeto que recibe save(), como haria el repositorio real */
		given(tutorialRepository.save(any(Tutorial.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// 2. WHEN (Cuando ejecutamos la acción que queremos probar)
		Tutorial creado = tutorialService.create(request);
		
		// 3. THEN (Entonces verificamos los resultados)
		// then: ArgumentCaptor captura el objeto que el servicio paso realmente a save()
		ArgumentCaptor<Tutorial> captor = ArgumentCaptor.forClass(Tutorial.class);
		verify(tutorialRepository).save(captor.capture());
		Tutorial guardado = captor.getValue();

		assertThat(guardado).isNotSameAs(request);
		assertThat(guardado.getId()).isZero();
		assertThat(guardado.getTitle()).isEqualTo("Nuevo");
		assertThat(guardado.getDescription()).isEqualTo("Descripcion");
		assertThat(guardado.isPublished()).isTrue();
		assertThat(guardado.getTags()).isEmpty();
		assertThat(creado).isSameAs(guardado);
	}

	@Test
	@DisplayName("Test para actualizar un tutorial existente")
	void testUpdate() {

		// given
		Tutorial cambios = Tutorial.builder()
				.title("Titulo editado")
				.description("Descripcion editada")
				.published(false)
				.build();

		given(tutorialRepository.findById(1L)).willReturn(Optional.of(tutorial1));
		given(tutorialRepository.save(tutorial1)).willReturn(tutorial1);

		// when
		Tutorial actualizado = tutorialService.update(1L, cambios);

		// then
		assertThat(actualizado.getId()).isEqualTo(1L);
		assertThat(actualizado.getTitle()).isEqualTo("Titulo editado");
		assertThat(actualizado.getDescription()).isEqualTo("Descripcion editada");
		assertThat(actualizado.isPublished()).isFalse();
		verify(tutorialRepository).save(tutorial1);
	}

	@Test
	@DisplayName("Test actualizar un tutorial que no existe: lanza excepcion y no guarda nada")
	void testUpdateNoEncontrado() {

		// given
		given(tutorialRepository.findById(99L)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> tutorialService.update(99L, tutorial2))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(tutorialRepository, never()).save(any());
	}

	@Test
	@DisplayName("Test para eliminar un tutorial por su id")
	void testDeleteById() {

		// when
		tutorialService.deleteById(1L);

		// then
		verify(tutorialRepository).deleteById(1L);
	}

	@Test
	@DisplayName("Test para eliminar todos los tutoriales")
	void testDeleteAll() {

		// when
		tutorialService.deleteAll();

		// then
		verify(tutorialRepository).deleteAll();
	}
}
