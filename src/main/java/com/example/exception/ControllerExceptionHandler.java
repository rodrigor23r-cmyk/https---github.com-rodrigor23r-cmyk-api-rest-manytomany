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

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

// La madre del cordero:
@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public ErrorMessage resourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        ErrorMessage message = new ErrorMessage(
                HttpStatus.NOT_FOUND.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));

        return message;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessage globalExceptionHandler(Exception ex, WebRequest request) {
        ErrorMessage message = new ErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));

        return message;
    }
}
