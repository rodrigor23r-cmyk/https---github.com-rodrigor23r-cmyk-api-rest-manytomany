# Plan: capa de servicio + tests unitarios e integración

Modelo a imitar: `victor-seguridad-api-demo/rest-api-demo-mostoles-backend-2026` (`src/main/java/com/example/services` y `src/test`)

Cómo ejecutar los tests (hace falta MySQL levantado y la variable `DB_PASSWORD` del `.env`):

```bash
set -a && source .env && set +a && ./mvnw test                                  # todos
set -a && source .env && set +a && ./mvnw test -Dtest=TutorialTest              # una clase
set -a && source .env && set +a && ./mvnw test -Dtest=TutorialTest#testAddTag   # un método
```

Patrón de todos los tests (el mismo que en el proyecto de referencia): `// given` → `// when` → `// then`.

## ✅ Resultado final (2026-10-05): 80 tests en verde

| Clase de test | Capa | Tipo | Tests |
|---|---|---|---|
| `RestApiManyToManyExampleApplicationTests` | Toda la app | Integración (`@SpringBootTest`) | 1 |
| `entities/TutorialTest` | Entidad | Unitario puro (JUnit + AssertJ) | 5 |
| `repository/TutorialRepositoryTest` | Repositorio | Integración con MySQL (`@DataJpaTest`) | 6 |
| `repository/TagRepositoryTest` | Repositorio | Integración con MySQL (`@DataJpaTest`) | 4 |
| `service/TutorialServiceImplTest` | Servicio | Unitario (Mockito) | 11 |
| `service/TagServiceImplTest` | Servicio | Unitario (Mockito) | 19 |
| `spring_security_jwt/service/UserDetailsServiceImplTest` | Servicio | Unitario (Mockito) | 2 |
| `controller/TutorialControllerTest` | Controlador | Integración (MockMvc + JWT real) | 16 |
| `controller/TagControllerTest` | Controlador | Integración (MockMvc + JWT real) | 16 |
| **Total** | | | **80** |

`controller/AbstractControllerTest` no tiene tests: es la clase base de los dos tests de controlador (configuración, mocks y login).

Comprobado ejecutando la suite completa con 4 órdenes de clases distintos (`-Dsurefire.runOrder=filesystem`, `reversealphabetical` y `random` ×2): siempre 80/80.

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
- [x] Añadir `@DisplayName("Test de carga del contexto de Spring")` a `contextLoads()`, como en la referencia
- [x] Crear los paquetes de test que espejan a `src/main`:
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
- [x] **Léelo y entiéndelo antes de seguir**: es tu plantilla para `TagService`

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

- [x] Clase sin anotaciones de Spring. `@BeforeEach` crea un `Tutorial` y dos `Tag` con el builder (dales `.id(1L)` y `.id(2L)`, porque `removeTag` busca por id)
- [x] `testAddTag`: después de `addTag(tag)`, el tag está en `tutorial.getTags()` **y** el tutorial está en `tag.getTutorials()` (los dos lados)
- [x] `testRemoveTag`: después de `addTag` + `removeTag(id)`, ninguno de los dos lados contiene al otro
- [x] `testRemoveTagInexistente`: `removeTag(99L)` no cambia nada ni lanza excepción
- [x] Ejecutar `-Dtest=TutorialTest` → verde

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

> ✅ **Hecho con un cambio respecto al plan**: `@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")`. Así este contexto no borra las tablas al arrancar (ver la trampa de la Fase 5). Como consecuencia, los tests no suponen que la base de datos está vacía: usan títulos únicos (`[repo-test]`) y comprueban solo sus propios datos.
>
> Tests extra: `testDeleteTutorialNoBorraTags` (borrar el lado propietario limpia `tutorials_tags` pero no borra los tags), `testSaveTag` y `testDeleteTagAsignadoQuitandoloAntes` (la misma técnica que usa `TagServiceImpl.deleteTag`).

### 3.1 `src/test/java/com/example/repository/TutorialRepositoryTest.java`
- [x] `@Autowired TutorialRepository` (y `TagRepository` si lo necesitas)
- [x] `testSaveTutorial`: `save()` devuelve un objeto con `getId() > 0`
- [x] `testFindByPublished`: guardar uno publicado y otro no; `findByPublished(true)` devuelve solo 1
- [x] `testFindByTitleContaining`: buscar por un trozo del título
- [x] `testSaveTutorialConTags`: `addTag` con tags **nuevos** + `save(tutorial)`. Comprueba que el cascade `PERSIST` les ha dado id a los tags
- [x] `testFindTutorialsByTagsId`: la consulta many-to-many. Dos tutoriales comparten un tag → la lista tiene tamaño 2

### 3.2 `src/test/java/com/example/repository/TagRepositoryTest.java`
- [x] `testFindTagsByTutorialsId`: un tutorial con 2 tags → la lista tiene tamaño 2
- [x] `testFindTagsByTutorialsIdSinTags`: un tutorial sin tags → lista vacía
- [x] Ejecutar `-Dtest='*RepositoryTest'` → verde

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
- [x] `testFindById`: given `findById(1L)` → `Optional.of(tutorial)` → devuelve ese tutorial
- [x] `testFindByIdNoEncontrado`: given `Optional.empty()` → `assertThatThrownBy(...).isInstanceOf(ResourceNotFoundException.class)`
- [x] `testCreate`: el tutorial guardado tiene `published = true` aunque el de entrada tenga `false` (usa `given(repo.save(any(Tutorial.class))).willAnswer(inv -> inv.getArgument(0))`)
- [x] `testUpdate`: los campos cambian y se llama a `save` → `verify(tutorialRepository).save(tutorial)`
- [x] `testUpdateNoEncontrado`: lanza la excepción **y** no se guarda nada → `verify(tutorialRepository, never()).save(any())`
- [x] `testFindAllVacio`: lista vacía → `assertThat(result).isEmpty()`

### 4.2 `src/test/java/com/example/service/TagServiceImplTest.java`
> Los tests usan los nombres reales de los métodos: `createTagWithTutorialId`, `updateTag`, `deleteTagFromTutorial` y `deleteTag`. Además de los casos de abajo, cubre también `findAll`, `findById`, `findTutorialsByTagsId`, `updateTag` y los casos de "no existe" de cada método.
- [x] `@Mock TagRepository` **y** `@Mock TutorialRepository`
- [x] `testAddTagNuevo`: un tag sin id → se añade al tutorial y se guarda
- [x] `testAddTagExistente`: un tag con id → se busca con `findById` y el tutorial lo contiene
- [x] `testAddTagExistenteNoEncontrado`: el tag trae id pero no existe → excepción
- [x] `testAddTagTutorialNoEncontrado`: el tutorial no existe → excepción
- [x] `testFindTagsByTutorialIdTutorialNoExiste`: excepción **y** `verify(tagRepository, never()).findTagsByTutorialsId(any())`
- [x] `testRemoveTagFromTutorial`: después de la llamada, el tutorial ya no contiene el tag
- [x] `testDeleteTagAsignado`: el tag se quita de sus tutoriales y se llama a `delete(tag)` (es el test del bug que arreglaste en 1.2)

### 4.3 `src/test/java/com/example/spring_security_jwt/service/UserDetailsServiceImplTest.java`
- [x] `@Mock UserRepository`, `@InjectMocks UserDetailsServiceImpl`
- [x] `@BeforeEach`: crear un `User` con el builder (email, username, password y un `Role` `ROLE_ADMIN`)
- [x] `testLoadUserByUsernameEncontrado`: given `findByEmail(...)` → `Optional.of(user)` → el username es correcto y tiene la authority `ROLE_ADMIN`
- [x] `testLoadUserByUsernameNoEncontrado`: `Optional.empty()` → `UsernameNotFoundException`

- [x] Ejecutar `-Dtest='*ServiceImplTest'` → verde

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
- [x] ⚠️ Aquí el login es por **email**, no por username: `LoginRequest.builder().email("admin@example.com").password("Temp2026$$")`
- [x] `POST /api/auth/signin` → leer `"token"` con `JSONObject` → `this.token = "Bearer " + ...`
- [x] El usuario existe porque `CreateSampleData` se ejecuta al arrancar el contexto. `UserRepository` **no** se mockea, para que el login funcione de verdad

### 5.2 `src/test/java/com/example/controller/TutorialControllerTest.java`
- [x] `@MockitoBean TutorialService` **y** `@MockitoBean TagService` (ver la trampa ⚠️ más abajo)
- [x] `GET /api/tutorials` con 2 tutoriales → `200` y `jsonPath("$.size()", is(2))`
- [x] `GET /api/tutorials` con lista vacía → `204 No Content`
- [x] `GET /api/tutorials?title=...` → se llama a `findByTitleContaining` y no a `findAll`
- [x] `GET /api/tutorials/{id}` existe → `200` y `jsonPath("$.title", ...)`
- [x] `GET /api/tutorials/{id}` no existe → `given(...).willThrow(new ResourceNotFoundException("..."))` → `404`
- [x] `POST /api/tutorials` con body JSON → `201`. Aquí no es multipart como en la referencia, es `.contentType(APPLICATION_JSON).content(json)`
- [x] `PUT /api/tutorials/{id}` → `200` y el título cambiado
- [x] `DELETE /api/tutorials/{id}` → `204` y `verify(tutorialService).deleteById(1L)`

### 5.3 `src/test/java/com/example/controller/TagControllerTest.java`
- [x] Mismos `@MockitoBean` que en 5.2 (los dos servicios)
- [x] `GET /api/tutorials/{id}/tags` → `200` y tamaño
- [x] `GET /api/tutorials/{id}/tags` con tutorial inexistente (`willThrow`) → `404`
- [x] `GET /api/tags/{id}/tutorials` → `200` y tamaño
- [x] `POST /api/tutorials/{id}/tags` → `201` y `jsonPath("$.name", ...)`
- [x] `DELETE /api/tutorials/{tid}/tags/{tagId}` → `204`
- [x] `DELETE /api/tags/{id}` → `204`

### 5.4 Tests de seguridad (no están en la referencia, pero son muy instructivos)
- [x] Petición sin cabecera `Authorization` → `401`
- [x] Login como `user@example.com` (rol USER) y `POST /api/tutorials` → espera `403`
  > 🐞 Este test **fallará con 500**: está confirmado, ver "Bugs confirmados". Escribe primero el test, míralo fallar en rojo y luego arréglalo. Así se trabaja con TDD.
  > ✅ Hecho así: primero falló en rojo (`Status expected:<403> but was:<500>` en 2 tests) y después del arreglo pasó a verde.
- [x] Arreglar: añadir a `ControllerExceptionHandler` un `@ExceptionHandler(AccessDeniedException.class)` con `@ResponseStatus(HttpStatus.FORBIDDEN)` (la clase es `org.springframework.security.access.AccessDeniedException`) → el test pasa a verde

### ⚠️ Trampa: contextos de Spring y la misma base de datos MySQL
Spring guarda en caché un contexto por cada combinación distinta de configuración de test. Cada contexto nuevo con `create-drop` **borra y recrea las tablas**. Si un `@DataJpaTest` arranca su contexto entre dos clases de controlador, los usuarios de `CreateSampleData` desaparecen y el login falla.
- Síntoma: los tests de controlador pasan sueltos (`-Dtest=TutorialControllerTest`), pero fallan con `./mvnw test` (401, o `JSONObject["token"] not found`)
- Para reducirlo: usa **exactamente los mismos** `@MockitoBean` en las dos clases de controlador, para que compartan contexto
- Si aun así pasa, lo hablamos: hay soluciones (H2 para `@DataJpaTest`, una base de datos de test aparte o `@WithMockUser`)

> ✅ **Resuelto así:**
> 1. `AbstractControllerTest` es una clase base con las anotaciones, los `@MockitoBean`, `MockMvc`, `ObjectMapper` y el método `obtenerToken(email)`. Las dos clases de controlador heredan de ella, así que su configuración es idéntica por construcción y comparten un único contexto (comprobado en los logs: un solo arranque).
> 2. Los `@DataJpaTest` usan `ddl-auto=update`, así que nunca borran las tablas ni los usuarios.

- [x] Ejecutar `-Dtest='*ControllerTest'` → verde (32 tests)

> Tests extra de seguridad: token falso → `401`; un USER puede leer (`200`); un USER no puede borrar tutoriales, borrar tags ni añadir tags (`403`, y se verifica que el servicio no se llama).

---

## Fase 6 · Cierre

- [x] `./mvnw test` completo → todo verde
- [x] Revisar que ningún test tenga `try/catch` que se trague el error (ver más abajo)
- [x] Commit

---

## 🐞 Bugs confirmados (probados con curl el 2026-10-05)

| Petición | Esperado | Real | Causa | Se arregla en |
|---|---|---|---|---|
| ~~`DELETE /api/tags/1` (tag asignado a tutoriales)~~ | `204` | ~~`500`, `foreign key constraint fails`~~ | `Tag` es el lado inverso (`mappedBy`): borrarlo no borra sus filas de `tutorials_tags` | ✅ Arreglado en Fase 1.2 |
| ~~`POST /api/tutorials` con rol USER~~ | `403` | ~~`500`, `"Access Denied"`~~ | `@ExceptionHandler(Exception.class)` también captura la `AccessDeniedException` de `@PreAuthorize` | ✅ Arreglado en Fase 5.4 (`ControllerExceptionHandler`) |

---

## ❌ Cosas de la referencia que NO conviene copiar tal cual

1. **`testSaveProduct` de `ProductControllerTest`** envuelve el `mockMvc.perform(...)` en `try { } catch (Exception e) { e.printStackTrace(); }`. Si el `andExpect` falla, se captura la excepción y el test **pasa igualmente**: nunca puede fallar. Usa `throws Exception` en la firma del método.
2. **`testFindAllProducts` de `ProductServiceImplTest`** hace `when(productServiceImpl.findAll())` sobre el servicio real, no sobre el mock. Funciona por casualidad, porque Mockito simula la última llamada que se hizo a un mock (`productDao.findAll()`). Lo correcto es `when(productDao.findAll()).thenReturn(...)`.
