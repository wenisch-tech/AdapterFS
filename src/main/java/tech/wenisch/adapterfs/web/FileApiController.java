package tech.wenisch.adapterfs.web;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.wenisch.adapterfs.fs.FilePage;
import tech.wenisch.adapterfs.fs.FilesystemService;

@RestController
@RequestMapping("/api/v1")
public class FileApiController {
    private final FilesystemService files;
    public FileApiController(FilesystemService files) { this.files = files; }

    @GetMapping("/exports") Object exports() { return files.exports(); }
    @GetMapping("/exports/{export}/entries")
    FilePage list(@PathVariable String export, @RequestParam(defaultValue = "") String path,
                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) throws IOException {
        return files.list(export, path, page, size);
    }
    @GetMapping("/exports/{export}/download")
    ResponseEntity<InputStreamResource> download(@PathVariable String export, @RequestParam String path) throws IOException {
        var source = files.readable(export, path);
        String type = Files.probeContentType(source);
        return ResponseEntity.ok().contentLength(Files.size(source))
                .contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(source.getFileName().toString()).build().toString())
                .body(new InputStreamResource(Files.newInputStream(source)));
    }
    @PostMapping(value="/exports/{export}/upload", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String,Object> upload(@PathVariable String export, @RequestParam(defaultValue="") String path,
                              @RequestParam(defaultValue="false") boolean overwrite,
                              @RequestPart("file") MultipartFile upload) throws IOException {
        String target = path.isBlank() ? upload.getOriginalFilename() : path + "/" + upload.getOriginalFilename();
        try (var input = upload.getInputStream()) { files.upload(export, target, input, overwrite); }
        return Map.of("path", target, "size", upload.getSize());
    }
    @PostMapping("/exports/{export}/directories")
    void mkdir(@PathVariable String export, @RequestBody PathRequest request) throws IOException { files.mkdir(export, request.path()); }
    @PutMapping("/exports/{export}/entries")
    void move(@PathVariable String export, @RequestBody MoveRequest request) throws IOException {
        files.move(export, request.source(), request.destination(), request.overwrite());
    }
    @DeleteMapping("/exports/{export}/entries")
    void delete(@PathVariable String export, @RequestParam String path) throws IOException { files.delete(export, path); }
    public record PathRequest(String path) {}
    public record MoveRequest(String source, String destination, boolean overwrite) {}
}
