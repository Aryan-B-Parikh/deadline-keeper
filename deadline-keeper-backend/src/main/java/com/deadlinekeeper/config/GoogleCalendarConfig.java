package com.deadlinekeeper.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "google.calendar")
@Getter
@Setter
public class GoogleCalendarConfig {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
