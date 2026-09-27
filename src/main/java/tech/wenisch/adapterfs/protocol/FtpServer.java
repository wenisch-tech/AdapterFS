package tech.wenisch.adapterfs.protocol;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.springframework.stereotype.Component;
import tech.wenisch.adapterfs.auth.CredentialsService;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Component
public class FtpServer {
    private final AdapterFsProperties properties; private final CredentialsService credentials; private final ProtocolRoot root;
    private org.apache.ftpserver.FtpServer server;
    public FtpServer(AdapterFsProperties properties, CredentialsService credentials, ProtocolRoot root) {
        this.properties = properties; this.credentials = credentials; this.root = root;
    }
    @PostConstruct
    void start() throws FtpException {
        if (!properties.getFtp().isEnabled()) return;
        FtpServerFactory factory = new FtpServerFactory(); ListenerFactory listener = new ListenerFactory();
        listener.setPort(properties.getFtp().getPort());
        DataConnectionConfigurationFactory data = new DataConnectionConfigurationFactory();
        data.setPassivePorts(properties.getFtp().getPassivePorts()); data.setActiveEnabled(false);
        if (properties.getFtp().getExternalAddress() != null && !properties.getFtp().getExternalAddress().isBlank())
            data.setPassiveExternalAddress(properties.getFtp().getExternalAddress());
        listener.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
        if (properties.getFtp().isTlsEnabled()) {
            if (properties.getFtp().getTlsKeystore() == null || properties.getFtp().getTlsKeystorePassword() == null)
                throw new FtpException("FTP TLS requires a keystore and password");
            SslConfigurationFactory ssl = new SslConfigurationFactory();
            ssl.setKeystoreFile(properties.getFtp().getTlsKeystore().toFile()); ssl.setKeystorePassword(properties.getFtp().getTlsKeystorePassword());
            listener.setSslConfiguration(ssl.createSslConfiguration()); listener.setImplicitSsl(false);
        }
        factory.addListener("default", listener.createListener());
        PropertiesUserManagerFactory users = new PropertiesUserManagerFactory(); var manager = users.createUserManager();
        BaseUser user = new BaseUser(); user.setName(credentials.get().username()); user.setPassword(credentials.get().password());
        user.setHomeDirectory(root.path().toString()); user.setAuthorities(List.of(new WritePermission())); manager.save(user);
        factory.setFtplets(java.util.Map.of("read-only-exports",new ReadOnlyFtpFtplet(properties.getExports().stream().filter(AdapterFsProperties.Export::readOnly).map(AdapterFsProperties.Export::name).collect(java.util.stream.Collectors.toSet()))));
        factory.setUserManager(manager); server = factory.createServer(); server.start();
    }
    @PreDestroy void stop() { if (server != null && !server.isStopped()) server.stop(); }
}
