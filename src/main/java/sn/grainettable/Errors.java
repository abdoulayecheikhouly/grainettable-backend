package sn.grainettable;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> business(ApiException e){return ResponseEntity.status(e.status).body(Map.of("error",e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("error",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).reduce((a,b)->a+" · "+b).orElse("Champs invalides")));}
 @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class}) ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("error","Vérifiez les champs de votre demande"));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(Exception e){return ResponseEntity.status(409).body(Map.of("error","Cette référence existe déjà ou la ressource est encore utilisée"));}
 @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> upload(Exception e){return ResponseEntity.status(413).body(Map.of("error","L’image dépasse 5 Mo"));}
}
