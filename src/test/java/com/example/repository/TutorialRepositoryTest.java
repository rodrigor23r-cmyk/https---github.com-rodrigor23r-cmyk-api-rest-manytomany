package com.example.repository;

import static org.assertj.core.api.Assertions.*;

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

/* La anotacion siguiente solamente prueba las entidades y los repositorios de datos,
 * no levanta todo el contexto de Spring (ni controladores, ni seguridad, ni CreateSampleData).
 * Cada test se ejecuta en una transaccion que se deshace (roll-back) al terminar.
 *
 * ddl-auto=update: el resto de tests (@SpringBootTest) usan create-drop, que BORRA y
 * recrea las tablas al arrancar cada contexto. Si este contexto tambien lo hiciera, se
 * borrarian los usuarios que CreateSampleData inserto para los tests de controlador y
 * su login fallaria segun el orden en que se ejecuten las clases. Con update las tablas
 * se reutilizan (o se crean si no existen) sin borrar nada.
 *
 * Consecuencia: la base de datos puede tener ya datos de ejemplo, asi que los tests
 * NO suponen que las tablas estan vacias; usan titulos unicos y comprueban sus propios datos. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")

/* Para usar la base de datos real (MySQL) en lugar de sustituirla por una en memoria (H2) */
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TutorialRepositoryTest {

	@Autowired
	private TutorialRepository tutorialRepository;

	@Autowired
	private TagRepository tagRepository;

	/* Permite forzar flush() (enviar los INSERT a MySQL) y clear() (vaciar la cache de
	 * Hibernate) para que las consultas siguientes lean de verdad de la base de datos */
	@Autowired
	private TestEntityManager entityManager;

	private Tutorial tutorialPublicado;
	private Tutorial tutorialNoPublicado;
	private Tag tagJava;
	private Tag tagSpring;

	@BeforeEach
	void setUp() {

		tutorialPublicado = Tutorial.builder()
				.title("Curso de JUnit [repo-test]")
				.description("Tests unitarios")
				.published(true)
				.build();

		tutorialNoPublicado = Tutorial.builder()
				.title("Curso de Mockito [repo-test]")
				.description("Mocks")
				.published(false)
				.build();

		tagJava = Tag.builder().name("Java [repo-test]").build();
		tagSpring = Tag.builder().name("Spring [repo-test]").build();
	}

	/* Persiste en MySQL y vacia la cache de Hibernate */
	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("Test para persistir un tutorial")
	void testSaveTutorial() {

		// when
		Tutorial tutorialGuardado = tutorialRepository.save(tutorialPublicado);

		// then
		assertThat(tutorialGuardado).isNotNull();
		assertThat(tutorialGuardado.getId()).isGreaterThan(0);
	}

	@Test
	@DisplayName("Test para recuperar solo los tutoriales publicados")
	void testFindByPublished() {

		// given
		tutorialRepository.saveAll(List.of(tutorialPublicado, tutorialNoPublicado));
		flushAndClear();

		// when
		List<Tutorial> publicados = tutorialRepository.findByPublished(true);

		// then
		assertThat(publicados)
				.extracting(Tutorial::getTitle)
				.contains(tutorialPublicado.getTitle())
				.doesNotContain(tutorialNoPublicado.getTitle());
	}

	@Test
	@DisplayName("Test para buscar tutoriales por un trozo del titulo")
	void testFindByTitleContaining() {

		// given
		tutorialRepository.saveAll(List.of(tutorialPublicado, tutorialNoPublicado));
		flushAndClear();

		// when
		List<Tutorial> encontrados = tutorialRepository.findByTitleContaining("JUnit [repo-test]");

		// then
		assertThat(encontrados)
				.extracting(Tutorial::getTitle)
				.containsExactly("Curso de JUnit [repo-test]");
	}

	@Test
	@DisplayName("Test para persistir un tutorial con tags nuevos (cascade PERSIST)")
	void testSaveTutorialConTags() {

		// given
		tutorialPublicado.addTag(tagJava);
		tutorialPublicado.addTag(tagSpring);

		// when: solo se guarda el tutorial, los tags se guardan por el cascade PERSIST
		Tutorial tutorialGuardado = tutorialRepository.save(tutorialPublicado);
		flushAndClear();

		// then: los tags tienen id, es decir, se han insertado en la tabla tags
		assertThat(tagJava.getId()).isGreaterThan(0);
		assertThat(tagSpring.getId()).isGreaterThan(0);

		// y al recuperar el tutorial de la base de datos tiene sus 2 tags (tabla tutorials_tags)
		Tutorial tutorialRecuperado = tutorialRepository.findById(tutorialGuardado.getId()).orElseThrow();
		assertThat(tutorialRecuperado.getTags())
				.extracting(Tag::getName)
				.containsExactlyInAnyOrder("Java [repo-test]", "Spring [repo-test]");
	}

	@Test
	@DisplayName("Test de la consulta many-to-many: tutoriales que tienen un tag")
	void testFindTutorialsByTagsId() {

		// given: los dos tutoriales comparten tagJava; solo uno tiene tagSpring
		tutorialPublicado.addTag(tagJava);
		tutorialPublicado.addTag(tagSpring);
		tutorialNoPublicado.addTag(tagJava);
		tutorialRepository.saveAll(List.of(tutorialPublicado, tutorialNoPublicado));
		flushAndClear();

		// when
		List<Tutorial> conTagJava = tutorialRepository.findTutorialsByTagsId(tagJava.getId());
		List<Tutorial> conTagSpring = tutorialRepository.findTutorialsByTagsId(tagSpring.getId());

		// then
		assertThat(conTagJava)
				.extracting(Tutorial::getTitle)
				.containsExactlyInAnyOrder("Curso de JUnit [repo-test]", "Curso de Mockito [repo-test]");
		assertThat(conTagSpring)
				.extracting(Tutorial::getTitle)
				.containsExactly("Curso de JUnit [repo-test]");
	}

	@Test
	@DisplayName("Borrar un tutorial borra sus filas de tutorials_tags pero NO sus tags")
	void testDeleteTutorialNoBorraTags() {

		// given
		tutorialPublicado.addTag(tagJava);
		Tutorial tutorialGuardado = tutorialRepository.save(tutorialPublicado);
		flushAndClear();

		// when: Tutorial es el lado propietario, asi que Hibernate borra antes sus filas de tutorials_tags
		tutorialRepository.deleteById(tutorialGuardado.getId());
		flushAndClear();

		// then: el tutorial ya no existe, pero el tag si (no hay cascade REMOVE)
		assertThat(tutorialRepository.findById(tutorialGuardado.getId())).isEmpty();
		assertThat(tagRepository.findById(tagJava.getId())).isPresent();
		assertThat(tutorialRepository.findTutorialsByTagsId(tagJava.getId())).isEmpty();
	}
}
