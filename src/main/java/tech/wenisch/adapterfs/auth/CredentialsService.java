package tech.wenisch.adapterfs.auth;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import org.springframework.stereotype.Service;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Service
public class CredentialsService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Credentials credentials;

    public CredentialsService(AdapterFsProperties properties, ObjectMapper mapper) throws IOException {
        Path state = properties.getStateDirectory();
        Files.createDirectories(state);
        Path file = state.resolve("credentials.json");
        Credentials stored = Files.exists(file) ? mapper.readValue(file.toFile(), Credentials.class) : null;
        var auth = properties.getAuth();
        credentials = new Credentials(
                value(auth.getUsername(), stored == null ? "admin" : stored.username()),
                value(auth.getPassword(), stored == null ? random(24) : stored.password()),
                value(auth.getS3AccessKey(), stored == null ? "AFS" + random(17).replace("-", "A") : stored.s3AccessKey()),
                value(auth.getS3SecretKey(), stored == null ? random(40) : stored.s3SecretKey()));
        if (!Files.exists(file)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), credentials);
            try { Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
            catch (UnsupportedOperationException ignored) { }
        }
    }

    private static String value(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }
    private static String random(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public Credentials get() { return credentials; }
    public record Credentials(String username, String password, String s3AccessKey, String s3SecretKey) {}
}
