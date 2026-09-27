package tech.wenisch.adapterfs.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("adapterfs")
public class AdapterFsProperties {
    private Path stateDirectory = Path.of("/var/lib/adapterfs");
    private List<Export> exports = new ArrayList<>(List.of(new Export("files", Path.of("/data"), false)));
    private final Auth auth = new Auth();
    private final Protocol webdav = new Protocol(true, 8080);
    private final Protocol s3 = new Protocol(true, 9000);
    private final Sftp sftp = new Sftp();
    private final Ftp ftp = new Ftp();
    private final Session session = new Session();

    public Path getStateDirectory() { return stateDirectory; }
    public void setStateDirectory(Path value) { stateDirectory = value; }
    public List<Export> getExports() { return exports; }
    public void setExports(List<Export> value) { exports = value; }
    public Auth getAuth() { return auth; }
    public Protocol getWebdav() { return webdav; }
    public Protocol getS3() { return s3; }
    public Sftp getSftp() { return sftp; }
    public Ftp getFtp() { return ftp; }
    public Session getSession() { return session; }

    public record Export(String name, Path path, boolean readOnly) {}
    public static class Auth {
        private String username;
        private String password;
        private String s3AccessKey;
        private String s3SecretKey;
        private List<String> oidcAllowedSubjects = new ArrayList<>();
        private List<String> oidcAllowedGroups = new ArrayList<>();
        public String getUsername() { return username; }
        public void setUsername(String v) { username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { password = v; }
        public String getS3AccessKey() { return s3AccessKey; }
        public void setS3AccessKey(String v) { s3AccessKey = v; }
        public String getS3SecretKey() { return s3SecretKey; }
        public void setS3SecretKey(String v) { s3SecretKey = v; }
        public List<String> getOidcAllowedSubjects() { return oidcAllowedSubjects; }
        public void setOidcAllowedSubjects(List<String> v) { oidcAllowedSubjects = v; }
        public List<String> getOidcAllowedGroups() { return oidcAllowedGroups; }
        public void setOidcAllowedGroups(List<String> v) { oidcAllowedGroups = v; }
    }
    public static class Protocol {
        private boolean enabled; private int port;
        public Protocol(boolean enabled, int port) { this.enabled = enabled; this.port = port; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public int getPort() { return port; }
        public void setPort(int v) { port = v; }
    }
    public static class Sftp extends Protocol {
        private Path authorizedKeys;
        public Sftp() { super(true, 2222); }
        public Path getAuthorizedKeys() { return authorizedKeys; }
        public void setAuthorizedKeys(Path v) { authorizedKeys = v; }
    }
    public static class Ftp extends Protocol {
        private String passivePorts = "30000-30009";
        private String externalAddress;
        private boolean tlsEnabled;
        private Path tlsKeystore;
        private String tlsKeystorePassword;
        public Ftp() { super(false, 2121); }
        public String getPassivePorts() { return passivePorts; }
        public void setPassivePorts(String v) { passivePorts = v; }
        public String getExternalAddress() { return externalAddress; }
        public void setExternalAddress(String v) { externalAddress = v; }
        public boolean isTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(boolean v) { tlsEnabled = v; }
        public Path getTlsKeystore() { return tlsKeystore; }
        public void setTlsKeystore(Path v) { tlsKeystore = v; }
        public String getTlsKeystorePassword() { return tlsKeystorePassword; }
        public void setTlsKeystorePassword(String v) { tlsKeystorePassword = v; }
    }
    public static class Session {
        private String stopToken; private long durationSeconds;
        public String getStopToken() { return stopToken; }
        public void setStopToken(String v) { stopToken = v; }
        public long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(long v) { durationSeconds = v; }
    }
}
