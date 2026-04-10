package tn.esprit.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

/**
 * Web configuration for CORS and security headers
 * Essential for camera access on HTTPS domains
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {



    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources with security headers
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600)
                .setCacheControl("no-cache, no-store, must-revalidate");
    }

    @Override
    public void configureMessageConverters(
            java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        // Add any custom message converters if needed
    }
}
