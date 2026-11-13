package ar.edu.ticketya.identidad.exception;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> application(ApiException ex){return ResponseEntity.status(ex.getStatus()).body(Map.of("error",ex.getMessage()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception ignored){return ResponseEntity.badRequest().body(Map.of("error","Solicitud inválida"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(DataIntegrityViolationException ignored){return ResponseEntity.status(409).body(Map.of("error","Identificador no disponible"));}
}
