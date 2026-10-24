package no.cantara.julebrus.config;

import no.cantara.julebrus.user.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    /**
     * Production: Google login. API calls that arrive without a session get 401 so
     * the SPA can redirect to /oauth2/authorization/google itself instead of being
     * bounced through an HTML redirect.
     */
    @Bean
    @Profile("!dev")
    public SecurityFilterChain googleChain(HttpSecurity http, AuthProperties auth) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/assets/**", "/manifest.webmanifest", "/sw.js",
                                "/workbox-*.js", "/icons/**", "/favicon.ico", "/actuator/health", "/api/config").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.oidcUserService(domainCheckingUserService(auth)))
                        .defaultSuccessUrl("/", true))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                .logout(l -> l.logoutUrl("/api/logout").logoutSuccessUrl("/"))
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        return http.build();
    }

    private static OAuth2UserService<OidcUserRequest, OidcUser> domainCheckingUserService(AuthProperties auth) {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            if (!auth.isAllowed(user.getEmail())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("domain_not_allowed",
                        "This Google account's domain is not invited to the tasting", null));
            }
            return user;
        };
    }

    /** Development: everyone is the configured dev user. No Google, no CSRF. */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devChain(HttpSecurity http, DevUserProperties dev) throws Exception {
        http
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(new DevUserFilter(dev), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
        return http.build();
    }

    static final class DevUserFilter extends org.springframework.web.filter.OncePerRequestFilter {
        private final DevUserProperties dev;

        DevUserFilter(DevUserProperties dev) {
            this.dev = dev;
        }

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                                        jakarta.servlet.http.HttpServletResponse res,
                                        jakarta.servlet.FilterChain chain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            // X-Dev-User lets you impersonate a second person from another browser tab
            // when testing group sessions locally.
            String email = req.getHeader("X-Dev-User");
            String name = email == null ? dev.name() : email.substring(0, email.indexOf('@'));
            var principal = new CurrentUser.DevPrincipal(email == null ? dev.email() : email, name);
            var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(token);
            chain.doFilter(req, res);
        }
    }
}
