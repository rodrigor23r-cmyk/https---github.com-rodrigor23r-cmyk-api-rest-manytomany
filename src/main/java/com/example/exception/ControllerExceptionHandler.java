/*@RestControllerAdvise

Rest Exception Handler with Controller Advice in Spring
Spring supports exception handling by a global Exception Handler (@ExceptionHandler) with Controller Advice (@RestControllerAdvice).
The @RestControllerAdvice annotation is a specialization of @Component annotation so that it is auto-detected via classpath scanning. It is a kind of interceptor that surrounds the logic in our Controllers and allows us to apply some common logic to them.

Rest Controller Advice’s methods (annotated with @ExceptionHandler) are shared globally across multiple @Controller components to capture exceptions and translate them to HTTP responses. The @ExceptionHandler annotation indicates which type of Exception we want to handle. The exception instance and the request will be injected via method arguments.

By using two annotations together, we can:
	- control the body of the response along with status code
	- handle several exceptions in the same method

    How about @ResponseStatus?
@RestControllerAdvice annotation tells a controller that the object returned is automatically serialized into JSON and passed to the HttpResponse object. You only need to return 
 
Java  body object instead of ResponseEntity object. But the status could be always OK (200) although the data corresponds to an exception signal (404 – Not Found for example). @ResponseStatus can help to set the HTTP status code for the response:
@RestControllerAdvice with @ResponseEntity
If you use @RestControllerAdvice without @ResponseBody and @ResponseStatus, you can return ResponseEntity object instead
*/


package com.example.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// La madre del cordero:
// F7: hereda de ResponseEntityExceptionHandler, que ya convierte ~15 excepciones de Spring MVC
// (id no numérico, JSON mal formado, método no soportado, Content-Type no soportado, ruta
// inexistente, validación...) en un ProblemDetail (RFC 9457) con su código HTTP correcto.
// Antes todas acababan en el @ExceptionHandler(Exception.class) y devolvían 500.
@RestControllerAdvice
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

    // F7: logger para registrar los errores inesperados (500) sin enseñarlos en la respuesta
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    // ================= Excepciones propias =================

    // F7: devuelve un ProblemDetail en lugar de ErrorMessage (el status ya va dentro del ProblemDetail)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail resourceNotFoundException(ResourceNotFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // F7: datos de entrada no válidos que detecta el servicio (p. ej. un tag nuevo sin nombre)
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequestException(BadRequestException ex) {
        return problema(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /*
     * @PreAuthorize lanza AccessDeniedException cuando el usuario está autenticado pero no
     * tiene el rol necesario (p. ej. un USER haciendo un POST). Sin este método la capturaba
     * el @ExceptionHandler(Exception.class) de abajo y devolvía 500 en lugar de 403.
     * Spring elige siempre el manejador de la excepción más específica.
     */
    // F7: ProblemDetail con un mensaje descriptivo en lugar del "Access Denied" genérico
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accessDeniedException(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operación");
    }

    // F7: login fallido (AuthController -> authenticationManager.authenticate). Antes daba 500.
    // Mismo mensaje si el email no existe o si la contraseña es incorrecta: así no se revela
    // qué emails están registrados.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail authenticationException(AuthenticationException ex) {
        String detail = (ex instanceof BadCredentialsException)
                ? "Email o contraseña incorrectos"
                : "No se ha podido autenticar al usuario";
        return problema(HttpStatus.UNAUTHORIZED, detail);
    }

    // F7: cualquier otro error es un fallo del servidor. El detalle real (que puede contener SQL,
    // nombres de clases, etc.) se escribe en el log y la respuesta lleva solo un mensaje genérico.
    @ExceptionHandler(Exception.class)
    public ProblemDetail globalExceptionHandler(Exception ex, WebRequest request) {
        LOGGER.error("Error inesperado en {}", request.getDescription(false), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error inesperado en el servidor. Inténtalo de nuevo más tarde.");
    }

    // ================= Excepciones de Spring MVC con mensajes más descriptivos =================

    // F7: validación de @Valid (p. ej. un tutorial sin título). Se añade el mapa "errors"
    // campo -> mensaje para que el cliente sepa exactamente qué corregir.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, "Los datos enviados no son válidos");
        problemDetail.setProperty("errors", errors);

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // F7: id no numérico en la URL (p. ej. /api/tutorials/abc). Antes daba 500.
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String parametro = (ex instanceof MethodArgumentTypeMismatchException mismatch)
                ? mismatch.getName()
                : ex.getPropertyName();
        String tipo = (ex.getRequiredType() != null) ? ex.getRequiredType().getSimpleName() : "otro tipo";

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                "El parámetro '" + parametro + "' debe ser de tipo " + tipo + " y se ha recibido '" + ex.getValue() + "'");

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // F7: cuerpo ausente o JSON mal formado. Antes daba 500 y el mensaje mostraba la firma
    // interna del método del controlador.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                "El cuerpo de la petición falta o no es un JSON válido");

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // F7: ruta que no existe. Antes daba 500 con el confuso "No static resource ...".
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status,
                "No existe ningún endpoint " + ex.getHttpMethod() + " /" + ex.getResourcePath());

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // F7: punto por el que pasan TODAS las respuestas de la clase base: se añade el timestamp
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        if (body instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty("timestamp", Instant.now());
        }

        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // F7: crea el ProblemDetail de los manejadores propios, con el mismo timestamp que los de la
    // clase base. Spring rellena "instance" con la ruta de la petición y "title" con el nombre del código.
    private ProblemDetail problema(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
