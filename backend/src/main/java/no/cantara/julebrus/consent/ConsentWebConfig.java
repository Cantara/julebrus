package no.cantara.julebrus.consent;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConsentWebConfig implements WebMvcConfigurer {

    private final ConsentInterceptor interceptor;

    public ConsentWebConfig(ConsentInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * Everything under /api needs consent except the endpoints a person needs in order
     * to give it, and the public ones: who am I (/api/me), the consent itself
     * (/api/consent, /api/consent/**), the public config (/api/config) and the public
     * privacy texts (/api/privacy). Keep this list, SecurityConfig's permitAll list and the
     * README in step. Static files and
     * the SPA shell are not under /api. Allow-list by exclusion, so a new endpoint is
     * protected by default.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/me", "/api/consent", "/api/consent/**", "/api/config", "/api/privacy");
    }
}
