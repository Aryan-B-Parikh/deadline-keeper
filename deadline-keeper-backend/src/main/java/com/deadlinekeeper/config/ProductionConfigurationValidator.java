package com.deadlinekeeper.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationValidator {
    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) return;

        require("DATABASE_URL");
        require("DATABASE_USERNAME");
        require("DATABASE_PASSWORD");
        require("SUPABASE_URL");
        require("APP_ENCRYPTION_KEY");
        require("SENDGRID_API_KEY");
        require("SENDGRID_FROM_EMAIL");
        require("GOOGLE_CALENDAR_CLIENT_ID");
        require("GOOGLE_CALENDAR_CLIENT_SECRET");

        String cors = environment.getProperty("cors.allowed-origins", "");
        if (cors.isBlank() || cors.contains("localhost") || cors.contains("127.0.0.1")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain only production origins");
        }

        String baseUrl = environment.getProperty("app.base-url", "");
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("APP_BASE_URL must use HTTPS in production");
        }
    }

    private void require(String environmentVariable) {
        String value = switch (environmentVariable) {
            case "DATABASE_URL" -> environment.getProperty("spring.datasource.url");
            case "DATABASE_USERNAME" -> environment.getProperty("spring.datasource.username");
            case "DATABASE_PASSWORD" -> environment.getProperty("spring.datasource.password");
            case "SUPABASE_URL" -> environment.getProperty("supabase.url");
            case "APP_ENCRYPTION_KEY" -> environment.getProperty("app.encryption-key");
            case "SENDGRID_API_KEY" -> environment.getProperty("sendgrid.api-key");
            case "SENDGRID_FROM_EMAIL" -> environment.getProperty("sendgrid.from-email");
            case "GOOGLE_CALENDAR_CLIENT_ID" -> environment.getProperty("google.calendar.client-id");
            case "GOOGLE_CALENDAR_CLIENT_SECRET" -> environment.getProperty("google.calendar.client-secret");
            default -> null;
        };
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured in production");
        }
    }
}
