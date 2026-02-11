package com.cloud.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    // Application-wide configurations can be added here
    // For example, CORS configuration, ModelMapper beans, etc.
}
