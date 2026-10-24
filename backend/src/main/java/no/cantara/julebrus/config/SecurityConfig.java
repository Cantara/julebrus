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
                                "/workbox-*.js", "/icons/**", "/favicon.ico", "/actuator/health", "/api/config", "/api/privacy", "/privacy", "/privacy/",
                                "/velkommen", "/velkommen/", "/welcome", "/welcome/", "/landing", "/landing/").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.oidcUserService(domainCheckingUserService(auth)))
                        .defaultSuccessUrl("/", true))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                .logout(l -> l.logoutUrl("/api/logout").logoutSuccessUrl("/"))
                .csrf(c -> c
                        .ignoringRequestMatchers("/mcp", "/mcp/**")
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Spring Security 6 loads the CSRF token lazily and only writes the cookie when
                // something reads it. A JSON API never does on a GET, so without this the first
                // POST after login (consent, start a session, join) has no token and gets 403.
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Reads the deferred CSRF token on every request so the XSRF-TOKEN cookie is issued. */
    static final class CsrfCookieFilter extends org.springframework.web.filter.OncePerRequestFilter {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                                        jakarta.servlet.http.HttpServletResponse res,
                                        jakarta.servlet.FilterChain chain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            org.springframework.security.web.csrf.CsrfToken token =
                    (org.springframework.security.web.csrf.CsrfToken) req.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
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
            // when testing group sessions locally. EventSource cannot set headers, so the
            // live stream takes the same thing as ?devUser= (dev profile only, like all of this).
            String email = req.getHeader("X-Dev-User");
            if (email == null) email = req.getParameter("devUser");
            if (email != null && email.indexOf('@') < 1) {
                res.setStatus(400);
                res.setContentType("application/json");
                res.getWriter().write("{\"code\":\"VALIDATION\",\"message\":\"X-Dev-User must be an email address\"}");
                return;
            }
            String name = email == null ? dev.name() : email.substring(0, email.indexOf('@'));
            var principal = new CurrentUser.DevPrincipal(email == null ? dev.email() : email, name);
            var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(token);
            chain.doFilter(req, res);
        }
    }
}
