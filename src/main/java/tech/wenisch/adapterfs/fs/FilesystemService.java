package tech.wenisch.adapterfs.fs;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Service
public class FilesystemService {
    private final Map<String, Root> roots = new LinkedHashMap<>();
    private final AdapterFsProperties properties;
    public FilesystemService(AdapterFsProperties properties) { this.properties = properties; }

    @PostConstruct
    void initialize() throws IOException {
        for (var configured : properties.getExports()) {
            if (configured.name() == null || !configured.name().matches("[a-z0-9][a-z0-9.-]{0,62}"))
                throw new IllegalArgumentException("Invalid export name: " + configured.name());
            Path path = configured.path().toAbsolutePath().normalize();
            Files.createDirectories(path);
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Export must be a real directory: " + path);
            if (roots.put(configured.name(), new Root(path.toRealPath(LinkOption.NOFOLLOW_LINKS), configured.readOnly())) != null)
                throw new IllegalArgumentException("Duplicate export: " + configured.name());
        }
        if (roots.isEmpty()) throw new IllegalArgumentException("At least one export is required");
    }

    public List<ExportInfo> exports() {
        return roots.entrySet().stream().map(e -> new ExportInfo(e.getKey(), e.getValue().readOnly())).toList();
    }
    public Path rootPath(String export) { return root(export).path(); }
    public boolean readOnly(String export) { return root(export).readOnly(); }

    public FilePage list(String export, String relative, int page, int size) throws IOException {
        Path directory = resolve(export, relative, true);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new NotDirectoryException(relative);
        List<FileEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (Files.isSymbolicLink(child)) continue;
                BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isDirectory() && !attrs.isRegularFile()) continue;
                String childRelative = root(export).path().relativize(child).toString().replace('\\', '/');
                entries.add(new FileEntry(child.getFileName().toString(), childRelative, attrs.isDirectory(),
                        attrs.isRegularFile() ? attrs.size() : 0, attrs.lastModifiedTime().toInstant()));
            }
        }
        entries.sort(Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));
        int boundedSize = Math.max(1, Math.min(size, 500));
        int boundedPage = Math.max(0, page);
        int from = Math.min(entries.size(), boundedPage * boundedSize);
        int to = Math.min(entries.size(), from + boundedSize);
        return new FilePage(export, cleanRelative(relative), entries.subList(from, to), boundedPage, boundedSize, entries.size());
    }

    public Path readable(String export, String relative) throws IOException {
        Path path = resolve(export, relative, true);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.NoSuchFileException(relative);
        return path;
    }

    public void upload(String export, String relative, InputStream data, boolean overwrite) throws IOException {
        checkWritable(export);
        Path target = resolve(export, relative, false);
        ensureParent(target);
        if (!overwrite && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(relative);
        Path temporary = target.resolveSibling("." + target.getFileName() + ".adapterfs-" + UUID.randomUUID() + ".part");
        try {
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                data.transferTo(output);
            }
            move(temporary, target, overwrite);
        } finally { Files.deleteIfExists(temporary); }
    }

    public void mkdir(String export, String relative) throws IOException {
        checkWritable(export); Path target = resolve(export, relative, false); ensureParent(target); Files.createDirectory(target);
    }
    public void move(String export, String source, String destination, boolean overwrite) throws IOException {
        checkWritable(export); Path from = resolve(export, source, true); Path to = resolve(export, destination, false); ensureParent(to);
        move(from, to, overwrite);
    }
    public void delete(String export, String relative) throws IOException {
        checkWritable(export); Path target = resolve(export, relative, true);
        if (target.equals(root(export).path())) throw new IllegalArgumentException("Cannot delete an export root");
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (var walk = Files.walk(target)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        } else Files.delete(target);
    }

    public Path resolve(String export, String relative, boolean mustExist) throws IOException {
        Root root = root(export); String cleaned = cleanRelative(relative);
        Path candidate = root.path().resolve(cleaned).normalize();
        if (!candidate.startsWith(root.path())) throw new IllegalArgumentException("Path escapes export");
        Path current = root.path();
        for (Path component : root.path().relativize(candidate)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IllegalArgumentException("Symbolic links are not accessible");
        }
        if (mustExist && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.NoSuchFileException(cleaned);
        return candidate;
    }
    private String cleanRelative(String value) {
        String input = value == null ? "" : value.replace('\\', '/');
        while (input.startsWith("/")) input = input.substring(1);
        Path normalized = Path.of(input).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) throw new IllegalArgumentException("Path escapes export");
        return normalized.toString().replace('\\', '/').equals(".") ? "" : normalized.toString().replace('\\', '/');
    }
    private void ensureParent(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
            throw new java.nio.file.NoSuchFileException("Parent directory does not exist");
    }
    private void move(Path source, Path destination, boolean overwrite) throws IOException {
        var options = overwrite ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try { Files.move(source, destination, options); }
        catch (AtomicMoveNotSupportedException e) {
            if (overwrite) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); else Files.move(source, destination);
        }
    }
    private void checkWritable(String export) { if (root(export).readOnly()) throw new ReadOnlyException(export); }
    private Root root(String name) { Root value = roots.get(name); if (value == null) throw new IllegalArgumentException("Unknown export: " + name); return value; }
    private record Root(Path path, boolean readOnly) {}
    public static class ReadOnlyException extends RuntimeException { public ReadOnlyException(String export) { super("Export is read-only: " + export); } }
    public static class FileAlreadyExistsException extends IOException { public FileAlreadyExistsException(String path) { super("Destination exists: " + path); } }
    public static class NotDirectoryException extends IOException { public NotDirectoryException(String path) { super("Not a directory: " + path); } }
}
