# Plan: capa de servicio + tests unitarios e integración

Modelo a imitar: `victor-seguridad-api-demo/rest-api-demo-mostoles-backend-2026` (`src/main/java/com/example/services` y `src/test`)

Cómo ejecutar los tests (hace falta MySQL levantado y la variable `DB_PASSWORD` del `.env`):

```bash
set -a && source .env && set +a && ./mvnw test                                  # todos
set -a && source .env && set +a && ./mvnw test -Dtest=TutorialTest              # una clase
set -a && source .env && set +a && ./mvnw test -Dtest=TutorialTest#testAddTag   # un método
```

Patrón de todos los tests (el mismo que en el proyecto de referencia): `// given` → `// when` → `// then`.

Qué prueba cada capa:

| Capa | Qué prueba su test | Fase |
|---|---|---|
| Entidad (JUnit puro) | `addTag()` / `removeTag()` sincronizan los dos lados | 2 |
| Repositorio (`@DataJpaTest`) | Las consultas SQL y el many-to-many | 3 |
| Servicio (Mockito) | La **lógica**: qué pasa si existe, si no existe, qué se guarda | 4 |
| Controlador (MockMvc) | Lo **HTTP**: rutas, códigos, JSON y seguridad (401/403) | 5 |

---

## Fase 0 · Preparación

- [x] Comprobar que el test actual pasa (`contextLoads`): 1 test, BUILD SUCCESS
- [ ] Añadir `@DisplayName("Test de carga del contexto de Spring")` a `contextLoads()`, como en la referencia
- [ ] Crear los paquetes de test que espejan a `src/main`:
  - `src/test/java/com/example/entities/`
  - `src/test/java/com/example/repository/`
  - `src/test/java/com/example/service/`
  - `src/test/java/com/example/controller/`
  - `src/test/java/com/example/spring_security_jwt/service/`

> No hay que tocar el `pom.xml`. JUnit 5, AssertJ, Mockito, MockMvc y `org.json.JSONObject` ya vienen con los starters `-test` que tienes.

---

## Fase 1 · Capa de servicio (refactorización, antes de los tests)

Reparto de responsabilidades:

| Controlador (se queda) | Servicio (se mueve) |
|---|---|
| `@GetMapping`, `@PreAuthorize`, `@PathVariable`… | Las llamadas a los repositorios |
| Elegir el código HTTP (p. ej. 204 si la lista está vacía) | Comprobar si existe y lanzar `ResourceNotFoundException` |
| Devolver el `ResponseEntity` | `@Transactional` y la lógica de `addTag` / `removeTag` |

### 1.1 `TutorialService` (ejemplo ya hecho)
- [x] `service/TutorialService.java` (interfaz) + `service/TutorialServiceImpl.java`
- [x] `TutorialController` usa `TutorialService` en lugar de `TutorialRepository`
- [x] Probado con curl: GET (todos, `?title=`, por id, `/published`), 404, POST, PUT, DELETE y 401 → igual que antes
- [ ] **Léelo y entiéndelo antes de seguir**: es tu plantilla para `TagService`

### 1.2 `TagService` (lo haces tú, imitando a `TutorialService`)
- [x] Crear `service/TagService.java` (interfaz) con estos métodos:
  - `List<Tag> findAll()`
  - `Tag findById(long id)` → lanza `ResourceNotFoundException` si no existe
  - `List<Tag> findTagsByTutorialId(long tutorialId)` → lanza la excepción si el **tutorial** no existe
  - `List<Tutorial> findTutorialsByTagId(long tagId)` → lanza la excepción si el **tag** no existe
  - `Tag addTagToTutorial(long tutorialId, Tag tagRequest)` → la lógica de "si trae id, el tag ya existe; si no, se crea uno nuevo"
  - `Tag update(long id, Tag tagRequest)`
  - `void removeTagFromTutorial(long tutorialId, long tagId)`
  - `void deleteById(long id)`
- [x] Crear `service/TagServiceImpl.java`. Necesita **los dos** repositorios: `TagRepository` y `TutorialRepository`
- [x] Hacer que `TagController` use solo `TagService` (ya no debe inyectar ningún repositorio)
- [x] 🐞 **Arreglar el bug de `deleteById`** (confirmado, ver "Bugs confirmados"): antes de borrar el tag, quítalo de cada tutorial que lo tenga
  > ⚠️ Pista: si recorres `tag.getTutorials()` y dentro llamas a `tutorial.removeTag(id)`, estarás modificando el mismo `Set` que recorres → `ConcurrentModificationException`. Recorre una copia: `new ArrayList<>(tag.getTutorials())`
- [x] Compilar: `./mvnw -o compile`
- [x] Probar todos los endpoints de tags (probado con curl el 2026-10-05), incluido:
  - [x] Añadir un tag **nuevo** a un tutorial
  - [x] Añadir un tag **existente** (`{"id": 1}`) a otro tutorial
  - [x] Quitar un tag de un tutorial
  - [x] Borrar un tag **asignado** a tutoriales → ahora `204`, no `500`

---

## Fase 2 · Test unitario puro de la entidad `Tutorial` (sin Spring)

Este no está en la referencia, pero es el test más sencillo para empezar. Solo usa JUnit y AssertJ: no arranca Spring ni toca la base de datos.

Archivo: `src/test/java/com/example/entities/TutorialTest.java`

- [ ] Clase sin anotaciones de Spring. `@BeforeEach` crea un `Tutorial` y dos `Tag` con el builder (dales `.id(1L)` y `.id(2L)`, porque `removeTag` busca por id)
- [ ] `testAddTag`: después de `addTag(tag)`, el tag está en `tutorial.getTags()` **y** el tutorial está en `tag.getTutorials()` (los dos lados)
- [ ] `testRemoveTag`: después de `addTag` + `removeTag(id)`, ninguno de los dos lados contiene al otro
- [ ] `testRemoveTagInexistente`: `removeTag(99L)` no cambia nada ni lanza excepción
- [ ] Ejecutar `-Dtest=TutorialTest` → verde

Pista: `import static org.assertj.core.api.Assertions.*;` → `assertThat(tutorial.getTags()).contains(tag);`

---

## Fase 3 · Tests de repositorio con `@DataJpaTest`

Imita a `ProductDaoTest`. Solo carga JPA y los repositorios, no los controladores ni la seguridad. Cada test se ejecuta en una transacción que se deshace (rollback) al terminar.

Anotaciones (Spring Boot 4, las mismas que en la referencia):
```java
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)   // usar MySQL real, no H2
```

### 3.1 `src/test/java/com/example/repository/TutorialRepositoryTest.java`
- [ ] `@Autowired TutorialRepository` (y `TagRepository` si lo necesitas)
- [ ] `testSaveTutorial`: `save()` devuelve un objeto con `getId() > 0`
- [ ] `testFindByPublished`: guardar uno publicado y otro no; `findByPublished(true)` devuelve solo 1
- [ ] `testFindByTitleContaining`: buscar por un trozo del título
- [ ] `testSaveTutorialConTags`: `addTag` con tags **nuevos** + `save(tutorial)`. Comprueba que el cascade `PERSIST` les ha dado id a los tags
- [ ] `testFindTutorialsByTagsId`: la consulta many-to-many. Dos tutoriales comparten un tag → la lista tiene tamaño 2

### 3.2 `src/test/java/com/example/repository/TagRepositoryTest.java`
- [ ] `testFindTagsByTutorialsId`: un tutorial con 2 tags → la lista tiene tamaño 2
- [ ] `testFindTagsByTutorialsIdSinTags`: un tutorial sin tags → lista vacía
- [ ] Ejecutar `-Dtest='*RepositoryTest'` → verde

> 💡 Si un test lee datos que acabas de guardar y no los encuentra, inyecta `TestEntityManager` y llama a `flush()` + `clear()` antes de consultar. Así obligas a que se lean de la base de datos y no de la caché de Hibernate.

---

## Fase 4 · Tests unitarios de servicio con Mockito

Imita a `ProductServiceImplTest`: `@ExtendWith(MockitoExtension.class)`, `@Mock` para los repositorios y `@InjectMocks` para el servicio. No arranca Spring y no toca la base de datos.

Herramientas que vas a necesitar:
```java
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;   // assertThat, assertThatThrownBy
```

### 4.1 `src/test/java/com/example/service/TutorialServiceImplTest.java`
- [ ] `testFindById`: given `findById(1L)` → `Optional.of(tutorial)` → devuelve ese tutorial
- [ ] `testFindByIdNoEncontrado`: given `Optional.empty()` → `assertThatThrownBy(...).isInstanceOf(ResourceNotFoundException.class)`
- [ ] `testCreate`: el tutorial guardado tiene `published = true` aunque el de entrada tenga `false` (usa `given(repo.save(any(Tutorial.class))).willAnswer(inv -> inv.getArgument(0))`)
- [ ] `testUpdate`: los campos cambian y se llama a `save` → `verify(tutorialRepository).save(tutorial)`
- [ ] `testUpdateNoEncontrado`: lanza la excepción **y** no se guarda nada → `verify(tutorialRepository, never()).save(any())`
- [ ] `testFindAllVacio`: lista vacía → `assertThat(result).isEmpty()`

### 4.2 `src/test/java/com/example/service/TagServiceImplTest.java`
- [ ] `@Mock TagRepository` **y** `@Mock TutorialRepository`
- [ ] `testAddTagNuevo`: un tag sin id → se añade al tutorial y se guarda
- [ ] `testAddTagExistente`: un tag con id → se busca con `findById` y el tutorial lo contiene
- [ ] `testAddTagExistenteNoEncontrado`: el tag trae id pero no existe → excepción
- [ ] `testAddTagTutorialNoEncontrado`: el tutorial no existe → excepción
- [ ] `testFindTagsByTutorialIdTutorialNoExiste`: excepción **y** `verify(tagRepository, never()).findTagsByTutorialsId(any())`
- [ ] `testRemoveTagFromTutorial`: después de la llamada, el tutorial ya no contiene el tag
- [ ] `testDeleteTagAsignado`: el tag se quita de sus tutoriales y se llama a `deleteById` (es el test del bug que arreglaste en 1.2)

### 4.3 `src/test/java/com/example/spring_security_jwt/service/UserDetailsServiceImplTest.java`
- [ ] `@Mock UserRepository`, `@InjectMocks UserDetailsServiceImpl`
- [ ] `@BeforeEach`: crear un `User` con el builder (email, username, password y un `Role` `ROLE_ADMIN`)
- [ ] `testLoadUserByUsernameEncontrado`: given `findByEmail(...)` → `Optional.of(user)` → el username es correcto y tiene la authority `ROLE_ADMIN`
- [ ] `testLoadUserByUsernameNoEncontrado`: `Optional.empty()` → `UsernameNotFoundException`

- [ ] Ejecutar `-Dtest='*ServiceImplTest'` → verde

---

## Fase 5 · Tests de integración de controladores con MockMvc + JWT

Imita a `ProductControllerTest`. Arranca toda la aplicación, incluida la seguridad, y hace peticiones HTTP simuladas. Los **servicios se simulan** con `@MockitoBean`, igual que la referencia simula `ProductService`.

Anotaciones e imports clave (Spring Boot 4):
```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;   // Jackson 3, no com.fasterxml
import org.json.JSONObject;

@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@AutoConfigureMockMvc
```

### 5.1 Login en `@BeforeEach` (igual que la referencia, con una diferencia)
- [ ] ⚠️ Aquí el login es por **email**, no por username: `LoginRequest.builder().email("admin@example.com").password("Temp2026$$")`
- [ ] `POST /api/auth/signin` → leer `"token"` con `JSONObject` → `this.token = "Bearer " + ...`
- [ ] El usuario existe porque `CreateSampleData` se ejecuta al arrancar el contexto. `UserRepository` **no** se mockea, para que el login funcione de verdad

### 5.2 `src/test/java/com/example/controller/TutorialControllerTest.java`
- [ ] `@MockitoBean TutorialService` **y** `@MockitoBean TagService` (ver la trampa ⚠️ más abajo)
- [ ] `GET /api/tutorials` con 2 tutoriales → `200` y `jsonPath("$.size()", is(2))`
- [ ] `GET /api/tutorials` con lista vacía → `204 No Content`
- [ ] `GET /api/tutorials?title=...` → se llama a `findByTitleContaining` y no a `findAll`
- [ ] `GET /api/tutorials/{id}` existe → `200` y `jsonPath("$.title", ...)`
- [ ] `GET /api/tutorials/{id}` no existe → `given(...).willThrow(new ResourceNotFoundException("..."))` → `404`
- [ ] `POST /api/tutorials` con body JSON → `201`. Aquí no es multipart como en la referencia, es `.contentType(APPLICATION_JSON).content(json)`
- [ ] `PUT /api/tutorials/{id}` → `200` y el título cambiado
- [ ] `DELETE /api/tutorials/{id}` → `204` y `verify(tutorialService).deleteById(1L)`

### 5.3 `src/test/java/com/example/controller/TagControllerTest.java`
- [ ] Mismos `@MockitoBean` que en 5.2 (los dos servicios)
- [ ] `GET /api/tutorials/{id}/tags` → `200` y tamaño
- [ ] `GET /api/tutorials/{id}/tags` con tutorial inexistente (`willThrow`) → `404`
- [ ] `GET /api/tags/{id}/tutorials` → `200` y tamaño
- [ ] `POST /api/tutorials/{id}/tags` → `201` y `jsonPath("$.name", ...)`
- [ ] `DELETE /api/tutorials/{tid}/tags/{tagId}` → `204`
- [ ] `DELETE /api/tags/{id}` → `204`

### 5.4 Tests de seguridad (no están en la referencia, pero son muy instructivos)
- [ ] Petición sin cabecera `Authorization` → `401`
- [ ] Login como `user@example.com` (rol USER) y `POST /api/tutorials` → espera `403`
  > 🐞 Este test **fallará con 500**: está confirmado, ver "Bugs confirmados". Escribe primero el test, míralo fallar en rojo y luego arréglalo. Así se trabaja con TDD.
- [ ] Arreglar: añadir a `ControllerExceptionHandler` un `@ExceptionHandler(AccessDeniedException.class)` con `@ResponseStatus(HttpStatus.FORBIDDEN)` (la clase es `org.springframework.security.access.AccessDeniedException`) → el test pasa a verde

### ⚠️ Trampa: contextos de Spring y la misma base de datos MySQL
Spring guarda en caché un contexto por cada combinación distinta de configuración de test. Cada contexto nuevo con `create-drop` **borra y recrea las tablas**. Si un `@DataJpaTest` arranca su contexto entre dos clases de controlador, los usuarios de `CreateSampleData` desaparecen y el login falla.
- Síntoma: los tests de controlador pasan sueltos (`-Dtest=TutorialControllerTest`), pero fallan con `./mvnw test` (401, o `JSONObject["token"] not found`)
- Para reducirlo: usa **exactamente los mismos** `@MockitoBean` en las dos clases de controlador, para que compartan contexto
- Si aun así pasa, lo hablamos: hay soluciones (H2 para `@DataJpaTest`, una base de datos de test aparte o `@WithMockUser`)

- [ ] Ejecutar `-Dtest='*ControllerTest'` → verde

---

## Fase 6 · Cierre

- [ ] `./mvnw test` completo → todo verde
- [ ] Revisar que ningún test tenga `try/catch` que se trague el error (ver más abajo)
- [ ] Commit

---

## 🐞 Bugs confirmados (probados con curl el 2026-10-05)

| Petición | Esperado | Real | Causa | Se arregla en |
|---|---|---|---|---|
| ~~`DELETE /api/tags/1` (tag asignado a tutoriales)~~ | `204` | ~~`500`, `foreign key constraint fails`~~ | `Tag` es el lado inverso (`mappedBy`): borrarlo no borra sus filas de `tutorials_tags` | ✅ Arreglado en Fase 1.2 |
| `POST /api/tutorials` con rol USER | `403` | `500`, `"Access Denied"` | `@ExceptionHandler(Exception.class)` también captura la `AccessDeniedException` de `@PreAuthorize` | Fase 5.4 |

---

## ❌ Cosas de la referencia que NO conviene copiar tal cual

1. **`testSaveProduct` de `ProductControllerTest`** envuelve el `mockMvc.perform(...)` en `try { } catch (Exception e) { e.printStackTrace(); }`. Si el `andExpect` falla, se captura la excepción y el test **pasa igualmente**: nunca puede fallar. Usa `throws Exception` en la firma del método.
2. **`testFindAllProducts` de `ProductServiceImplTest`** hace `when(productServiceImpl.findAll())` sobre el servicio real, no sobre el mock. Funciona por casualidad, porque Mockito simula la última llamada que se hizo a un mock (`productDao.findAll()`). Lo correcto es `when(productDao.findAll()).thenReturn(...)`.
