package tech.wenisch.adapterfs.webdav;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.wenisch.adapterfs.fs.FilesystemService;

@Configuration
public class WebDavConfig {
    @Bean ServletRegistrationBean<WebDavController> webDavServlet(FilesystemService files) {
        var registration = new ServletRegistrationBean<>(new WebDavController(files), "/dav/*");
        registration.setName("webdav"); registration.setLoadOnStartup(1); return registration;
    }
}
