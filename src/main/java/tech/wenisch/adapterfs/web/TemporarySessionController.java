package tech.wenisch.adapterfs.web;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@RestController
@RequestMapping("/api/v1/session")
public class TemporarySessionController {
    private final AdapterFsProperties properties; private final ConfigurableApplicationContext context;
    public TemporarySessionController(AdapterFsProperties properties, ConfigurableApplicationContext context) { this.properties = properties; this.context = context; }
    @PostConstruct void expiration() {
        if (properties.getSession().getDurationSeconds() > 0) {
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).schedule(this::shutdown,
                    properties.getSession().getDurationSeconds(), TimeUnit.SECONDS);
        }
    }
    @PostMapping("/stop") ResponseEntity<Map<String,String>> stop(@RequestHeader(value="Authorization", required=false) String authorization) {
        String token = properties.getSession().getStopToken();
        if (token == null || token.isBlank() || !(("Bearer " + token).equals(authorization)))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "forbidden"));
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).schedule(this::shutdown, 250, TimeUnit.MILLISECONDS);
        return ResponseEntity.accepted().body(Map.of("status", "stopping"));
    }
    private void shutdown() { context.close(); }
}
