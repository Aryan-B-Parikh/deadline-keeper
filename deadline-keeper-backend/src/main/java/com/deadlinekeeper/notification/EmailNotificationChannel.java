package com.deadlinekeeper.notification;

import com.deadlinekeeper.config.SendGridConfig;
import com.deadlinekeeper.model.User;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final SendGrid sendGrid;
    private final SendGridConfig config;

    public EmailNotificationChannel(SendGridConfig config) {
        this.config = config;
        this.sendGrid = new SendGrid(config.getApiKey());
    }

    @Override
    public void send(User user, String title, String message, String idempotencyKey) {
        try {
            Email from = new Email(config.getFromEmail(), "DeadlineKeeper");
            Email to = new Email(user.getEmail());
            Content content = new Content("text/html", buildHtmlContent(title, message));
            Mail mail = new Mail(from, title, to, content);

            // Attach deterministic delivery identity as correlation and telemetry metadata.
            // Note: SendGrid custom_args provides tracking and webhook event reconciliation.
            // Authoritative at-least-once delivery with crash recovery is guaranteed by the
            // PostgreSQL notification_outbox table (atomic SKIP LOCKED claim and leased transitions).
            if (idempotencyKey != null) {
                mail.addCustomArg("idempotency_key", idempotencyKey);
                mail.addCustomArg("delivery_id", idempotencyKey.replace("reminder:", ""));
            }

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 400) {
                throw new RuntimeException("SendGrid returned status " + response.getStatusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @Override
    public String getChannelName() {
        return "email";
    }

    private String buildHtmlContent(String title, String message) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #4F46E5; color: white; padding: 20px; border-radius: 8px 8px 0 0;">
                    <h1 style="margin: 0; font-size: 20px;">⏰ DeadlineKeeper</h1>
                </div>
                <div style="padding: 20px; border: 1px solid #e5e7eb; border-radius: 0 0 8px 8px;">
                    <h2 style="color: #1f2937;">%s</h2>
                    <p style="color: #4b5563; line-height: 1.6;">%s</p>
                    <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 20px 0;">
                    <p style="color: #9ca3af; font-size: 12px;">
                        You received this from DeadlineKeeper based on your notification preferences.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(title, message);
    }
}
