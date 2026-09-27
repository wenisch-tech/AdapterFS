package tech.wenisch.adapterfs.webdav;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.wenisch.adapterfs.fs.FilesystemService;

public class WebDavController extends HttpServlet {
    private final FilesystemService files;
    public WebDavController(FilesystemService files) { this.files = files; }

    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tail = request.getPathInfo() == null ? "" : request.getPathInfo().replaceFirst("^/", "");
        int slash = tail.indexOf('/');
        String export = slash < 0 ? tail : tail.substring(0, slash);
        String relative = slash < 0 ? "" : tail.substring(slash + 1);
        try {
            switch (request.getMethod()) {
                case "OPTIONS" -> { response.setHeader("DAV", "1"); response.setHeader("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, MKCOL, COPY, MOVE, PROPFIND"); response.setStatus(204); }
                case "GET", "HEAD" -> get(export, relative, request, response);
                case "PUT" -> { files.upload(export, relative, request.getInputStream(), true); response.setStatus(201); }
                case "DELETE" -> { files.delete(export, relative); response.setStatus(204); }
                case "MKCOL" -> { files.mkdir(export, relative); response.setStatus(201); }
                case "MOVE" -> { destination(export, relative, request); response.setStatus(201); }
                case "COPY" -> { copy(export, relative, request); response.setStatus(201); }
                case "PROPFIND" -> propfind(export, relative, request, response);
                default -> response.sendError(405);
            }
        } catch (FilesystemService.ReadOnlyException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, exception.getMessage());
        } catch (FilesystemService.FileAlreadyExistsException exception) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, exception.getMessage());
        } catch (NoSuchFileException exception) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        }
    }

    private void get(String export, String relative, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path path = files.readable(export, relative); long length = Files.size(path);
        response.setContentLengthLong(length); response.setContentType(contentType(path));
        if (!request.getMethod().equals("HEAD")) try (var input = Files.newInputStream(path); var output = response.getOutputStream()) { input.transferTo(output); }
    }
    private void propfind(String export, String relative, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path path = files.resolve(export, relative, true);
        response.setStatus(207); response.setContentType("application/xml; charset=utf-8");
        String base = "/dav/" + xml(export) + (relative.isBlank() ? "/" : "/" + pathEncode(relative));
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:multistatus xmlns:D=\"DAV:\">");
        appendResponse(body, base, path);
        if (Files.isDirectory(path) && !"0".equals(request.getHeader("Depth"))) {
            try (var children = Files.list(path)) { for (Path child : children.filter(p -> !Files.isSymbolicLink(p)).toList()) appendResponse(body, base + (base.endsWith("/") ? "" : "/") + pathEncode(child.getFileName().toString()), child); }
        }
        body.append("</D:multistatus>"); response.getWriter().write(body.toString());
    }
    private void appendResponse(StringBuilder out, String href, Path path) throws IOException {
        var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        out.append("<D:response><D:href>").append(xml(href)).append(attrs.isDirectory() && !href.endsWith("/") ? "/" : "")
           .append("</D:href><D:propstat><D:prop><D:displayname>").append(xml(path.getFileName().toString()))
           .append("</D:displayname><D:getlastmodified>").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(attrs.lastModifiedTime().toInstant().atZone(ZoneOffset.UTC)))
           .append("</D:getlastmodified>");
        if (attrs.isDirectory()) out.append("<D:resourcetype><D:collection/></D:resourcetype>");
        else out.append("<D:resourcetype/><D:getcontentlength>").append(attrs.size()).append("</D:getcontentlength><D:getcontenttype>").append(contentType(path)).append("</D:getcontenttype>");
        out.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>");
    }
    private void destination(String export, String source, HttpServletRequest request) throws IOException {
        URI uri = URI.create(request.getHeader("Destination")); String prefix = "/dav/" + export + "/";
        if (!uri.getPath().startsWith(prefix)) throw new IllegalArgumentException("Cross-export operations are not allowed");
        files.move(export, source, uri.getPath().substring(prefix.length()), "T".equalsIgnoreCase(request.getHeader("Overwrite")));
    }
    private void copy(String export, String source, HttpServletRequest request) throws IOException {
        URI uri = URI.create(request.getHeader("Destination")); String prefix = "/dav/" + export + "/";
        if (!uri.getPath().startsWith(prefix)) throw new IllegalArgumentException("Cross-export operations are not allowed");
        String destination = uri.getPath().substring(prefix.length());
        boolean overwrite = "T".equalsIgnoreCase(request.getHeader("Overwrite"));
        Path sourcePath = files.resolve(export, source, true);
        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            try (var input = Files.newInputStream(files.readable(export, source))) { files.upload(export, destination, input, overwrite); }
            return;
        }
        if (Files.exists(files.resolve(export, destination, false), LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) throw new FilesystemService.FileAlreadyExistsException(destination);
            files.delete(export, destination);
        }
        try (var paths = Files.walk(sourcePath)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic links are not accessible");
                String suffix = sourcePath.relativize(path).toString().replace('\\', '/');
                String target = suffix.isEmpty() ? destination : destination + "/" + suffix;
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) files.mkdir(export, target);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    try (var input = Files.newInputStream(path)) { files.upload(export, target, input, false); }
                }
            }
        }
    }
    private static String contentType(Path path) throws IOException { String value = Files.probeContentType(path); return value == null ? "application/octet-stream" : value; }
    private static String pathEncode(String value) { return value.replace(" ", "%20"); }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
