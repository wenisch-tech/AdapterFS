package tech.wenisch.adapterfs;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdapterFsApplication {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--print-credentials")) {
            printCredentials(); return;
        }
        SpringApplication.run(AdapterFsApplication.class, args);
    }

    private static void printCredentials() {
        try {
            Path state = Path.of(System.getenv().getOrDefault("ADAPTERFS_STATE_DIRECTORY", "/var/lib/adapterfs"));
            var node = new tools.jackson.databind.ObjectMapper().readTree(Files.readString(state.resolve("credentials.json")));
            System.out.println("username=" + node.get("username").asText());
            System.out.println("password=" + node.get("password").asText());
            System.out.println("s3AccessKey=" + node.get("s3AccessKey").asText());
            System.out.println("s3SecretKey=" + node.get("s3SecretKey").asText());
        } catch (Exception exception) {
            System.err.println("Unable to read AdapterFS credentials: " + exception.getMessage()); System.exit(2);
        }
    }
}
