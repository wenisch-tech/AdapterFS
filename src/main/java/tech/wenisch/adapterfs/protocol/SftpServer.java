package tech.wenisch.adapterfs.protocol;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.stereotype.Component;
import tech.wenisch.adapterfs.auth.CredentialsService;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Component
public class SftpServer {
    private final AdapterFsProperties properties; private final CredentialsService credentials; private final ProtocolRoot root;
    private SshServer server;
    public SftpServer(AdapterFsProperties properties, CredentialsService credentials, ProtocolRoot root) {
        this.properties = properties; this.credentials = credentials; this.root = root;
    }
    @PostConstruct
    void start() throws IOException {
        if (!properties.getSftp().isEnabled()) return;
        server = SshServer.setUpDefaultServer(); server.setPort(properties.getSftp().getPort());
        var keyFile = properties.getStateDirectory().resolve("ssh-host-key.ser");
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(keyFile));
        server.setPasswordAuthenticator((username, password, session) ->
                credentials.get().username().equals(username) && credentials.get().password().equals(password));
        if (properties.getSftp().getAuthorizedKeys() != null && Files.isRegularFile(properties.getSftp().getAuthorizedKeys())) {
            var authorized = new AuthorizedKeysAuthenticator(properties.getSftp().getAuthorizedKeys());
            server.setPublickeyAuthenticator((username,key,session) -> credentials.get().username().equals(username) && authorized.authenticate(username,key,session));
        }
        server.setFileSystemFactory(new VirtualFileSystemFactory(root.path()));
        var builder = new SftpSubsystemFactory.Builder();
        builder.addSftpEventListener(new ReadOnlySftpListener(properties.getExports().stream().map(AdapterFsProperties.Export::name).collect(java.util.stream.Collectors.toSet()),
                properties.getExports().stream().filter(AdapterFsProperties.Export::readOnly).map(AdapterFsProperties.Export::name).collect(java.util.stream.Collectors.toSet())));
        server.setSubsystemFactories(java.util.List.of(builder.build()));
        server.start();
    }
    @PreDestroy void stop() throws IOException { if (server != null) server.stop(); }
}
