package tech.wenisch.adapterfs.auth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

@Configuration
public class SecurityConfig {
    @Bean WebSecurityCustomizer webSecurityCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(java.util.List.of("DELETE","GET","HEAD","OPTIONS","PATCH","POST","PUT","COPY","MKCOL","MOVE","PROPFIND"));
        return web -> web.httpFirewall(firewall);
    }
    @Bean
    UserDetailsService users(CredentialsService credentials) {
        var value = credentials.get();
        return new InMemoryUserDetailsManager(User.withUsername(value.username())
                .password("{noop}" + value.password()).roles("USER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> clients,
                                 AdapterFsProperties properties, LoginThrottle throttle) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/assets/**", "/login", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/session/stop").permitAll()
                .requestMatchers("/dav/**", "/api/**", "/", "/files/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(login -> login.loginPage("/login")
                    .successHandler((request,response,authentication)->{throttle.succeeded(request.getRemoteAddr());response.sendRedirect("/");})
                    .failureHandler((request,response,exception)->{throttle.failed(request.getRemoteAddr());response.sendRedirect("/login?error");}).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/dav/**", "/api/v1/session/stop"))
            .httpBasic(Customizer.withDefaults());
        http.addFilterBefore(throttle, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        if (clients.getIfAvailable() != null) {
            OidcUserService delegate = new OidcUserService();
            http.oauth2Login(oauth -> oauth.userInfoEndpoint(info -> info.oidcUserService(request -> {
                var user = delegate.loadUser(request);
                boolean subject = properties.getAuth().getOidcAllowedSubjects().contains(user.getSubject());
                var groups = user.getClaimAsStringList("groups");
                boolean group = groups != null && groups.stream().anyMatch(properties.getAuth().getOidcAllowedGroups()::contains);
                if (!subject && !group) throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "OIDC subject is not allowed");
                return user;
            })));
        }
        return http.build();
    }
}
