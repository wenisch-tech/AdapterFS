package tech.wenisch.adapterfs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Component
public class ProtocolRoot {
    private final Path path;
    public ProtocolRoot(AdapterFsProperties properties) throws IOException {
        path = properties.getStateDirectory().resolve("protocol-root");
        Files.createDirectories(path);
        for (var item : properties.getExports()) {
            Path link = path.resolve(item.name());
            if (Files.exists(link) && !Files.isSymbolicLink(link)) throw new IOException("Protocol root entry is not a managed link: " + link);
            if (!Files.exists(link)) Files.createSymbolicLink(link, item.path().toAbsolutePath().normalize());
        }
    }
    public Path path() { return path; }
}
