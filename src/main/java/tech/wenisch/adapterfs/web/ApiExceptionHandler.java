package tech.wenisch.adapterfs.web;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.wenisch.adapterfs.fs.FilesystemService;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchFileException.class)
    ResponseEntity<ApiError> missing(Exception e) { return error(HttpStatus.NOT_FOUND, "not_found", e); }
    @ExceptionHandler(FilesystemService.FileAlreadyExistsException.class)
    ResponseEntity<ApiError> conflict(Exception e) { return error(HttpStatus.CONFLICT, "destination_exists", e); }
    @ExceptionHandler(FilesystemService.ReadOnlyException.class)
    ResponseEntity<ApiError> readOnly(Exception e) { return error(HttpStatus.FORBIDDEN, "read_only", e); }
    @ExceptionHandler({IllegalArgumentException.class, FilesystemService.NotDirectoryException.class})
    ResponseEntity<ApiError> badRequest(Exception e) { return error(HttpStatus.BAD_REQUEST, "invalid_request", e); }
    @ExceptionHandler(IOException.class)
    ResponseEntity<ApiError> io(Exception e) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "filesystem_error", e); }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, Exception e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
    }
}
