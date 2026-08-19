package com.deadlinekeeper.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@ConfigurationProperties(prefix = "sendgrid")
@Getter
@Setter
public class SendGridConfig {
    private String apiKey;
    private String fromEmail;
    private String inboxParseDomain;
    private String webhookSecret;
    private String webhookToken;

    private final Environment environment;

    public SendGridConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            if (webhookToken == null || webhookToken.isBlank()) {
                throw new IllegalStateException(
                        "SENDGRID_WEBHOOK_TOKEN (sendgrid.webhook-token) must be configured in production environment.");
            }
        }
    }
}

