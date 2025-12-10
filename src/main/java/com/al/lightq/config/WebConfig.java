package com.al.lightq.config;

import com.al.lightq.util.LightQConstants;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;

    public WebConfig(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Bean
    public RateLimitingInterceptor rateLimitingInterceptor() {
        return new RateLimitingInterceptor(rateLimitProperties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitingInterceptor())
                .addPathPatterns(
                        LightQConstants.QUEUE_BASE_URL + LightQConstants.PUSH_URL,
                        LightQConstants.QUEUE_BASE_URL + LightQConstants.POP_URL
                );
    }
}
