package tech.wenisch.adapterfs.auth;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.wenisch.adapterfs.config.AdapterFsProperties;
import tools.jackson.databind.ObjectMapper;

class CredentialsServiceTest {
    @TempDir Path state;
    @Test void generatedCredentialsPersistAcrossRestart() throws Exception {
        AdapterFsProperties properties=new AdapterFsProperties();properties.setStateDirectory(state);
        var first=new CredentialsService(properties,new ObjectMapper()).get();var second=new CredentialsService(properties,new ObjectMapper()).get();
        assertThat(second).isEqualTo(first);assertThat(first.password()).hasSizeGreaterThan(20);assertThat(first.s3AccessKey()).startsWith("AFS");
    }
    @Test void explicitCredentialsTakePrecedence() throws Exception {
        AdapterFsProperties properties=new AdapterFsProperties();properties.setStateDirectory(state);properties.getAuth().setUsername("operator");properties.getAuth().setPassword("configured");
        assertThat(new CredentialsService(properties,new ObjectMapper()).get().username()).isEqualTo("operator");
    }
}
