package com.transithub.backend.service;

import com.transithub.backend.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String SENDGRID_ENDPOINT = "https://api.sendgrid.com/v3/mail/send";
    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final JavaMailSender mailSender;

    // Railway blocks outbound SMTP (both 587 and 465 time out), so mail goes
    // over an HTTPS API when a key is present. Preference order is SendGrid,
    // then Brevo, then SMTP as the fallback for local runs, where it works fine.
    @Value("${sendgrid.api-key:}")
    private String sendGridApiKey;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.from:}")
    private String fromEmail;

    @Value("${app.mail.from-name:TransitHub}")
    private String fromName;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void sendReceipt(Booking booking) {
        if (!isConfigured()) return;
        try {
            String to = booking.getUser().getEmail();
            String origin, destination, dep;
            if (booking.getSchedule() != null && booking.getSchedule().getRoute() != null) {
                origin = booking.getSchedule().getRoute().getOrigin();
                destination = booking.getSchedule().getRoute().getDestination();
                dep = booking.getSchedule().getDepartsAt()
                        .format(DateTimeFormatter.ofPattern("EEE dd MMM yyyy, HH:mm"));
            } else {
                origin = booking.getOrigin() != null ? booking.getOrigin() : "";
                destination = booking.getDestination() != null ? booking.getDestination() : "";
                dep = booking.getDepartsAt() != null ? booking.getDepartsAt() : "";
            }
            String ref = booking.getId().toString().toUpperCase().substring(0, 8);
            String amount = booking.getTotalAmount().toPlainString();

            send(to,
                 "TransitHub Booking Confirmed – " + origin + " → " + destination,
                 buildHtml(origin, destination, dep, String.valueOf(booking.getSeatNumber()), amount, ref));
        } catch (Exception e) {
            // A receipt is a nice-to-have; never fail the booking over it.
            System.err.println("TransitHub: Failed to send receipt email – " + e.getMessage());
        }
    }

    /**
     * Unlike sendReceipt, this one throws on failure — if the code never
     * reaches the inbox the user can't finish signing up, so the caller has to
     * know rather than leave them stuck on the verify screen.
     */
    public void sendVerificationCode(String to, String name, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mail is not configured (needs BREVO_API_KEY and MAIL_FROM)");
        }
        send(to, code + " is your TransitHub verification code", buildCodeHtml(name, code));
    }

    /**
     * A friendly "your account is ready" email sent right after sign-up.
     * Fire-and-forget like the receipt — a welcome message is nice to have and
     * must never block or fail account creation.
     */
    public void sendWelcome(String to, String name) {
        if (!isConfigured()) return;
        try {
            send(to, "Welcome to TransitHub", buildWelcomeHtml(name));
        } catch (Exception e) {
            System.err.println("TransitHub: Failed to send welcome email – " + e.getMessage());
        }
    }

    private boolean isConfigured() {
        return fromEmail != null && !fromEmail.isBlank();
    }

    private void send(String to, String subject, String html) {
        if (sendGridApiKey != null && !sendGridApiKey.isBlank()) {
            sendViaSendGrid(to, subject, html);
        } else if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevo(to, subject, html);
        } else {
            sendViaSmtp(to, subject, html);
        }
    }

    private void sendViaSendGrid(String to, String subject, String html) {
        String payload = """
            {"personalizations":[{"to":[{"email":"%s"}]}],"from":{"email":"%s","name":"%s"},"subject":"%s","content":[{"type":"text/html","value":"%s"}]}
            """.formatted(json(to), json(fromEmail), json(fromName), json(subject), json(html));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SENDGRID_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + sendGridApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // SendGrid returns 202 Accepted on success; any non-2xx is a failure.
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "SendGrid rejected the message (HTTP " + response.statusCode() + "): " + response.body());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach SendGrid: " + e.getMessage(), e);
        }
    }

    private void sendViaBrevo(String to, String subject, String html) {
        String payload = """
            {"sender":{"name":"%s","email":"%s"},"to":[{"email":"%s"}],"subject":"%s","htmlContent":"%s"}
            """.formatted(json(fromName), json(fromEmail), json(to), json(subject), json(html));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", brevoApiKey)
                    .header("content-type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Brevo rejected the message (HTTP " + response.statusCode() + "): " + response.body());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not reach Brevo: " + e.getMessage(), e);
        }
    }

    private void sendViaSmtp(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("Could not send over SMTP: " + e.getMessage(), e);
        }
    }

    /** Minimal JSON string escaping for the values interpolated into the payload. */
    private String json(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private String buildWelcomeHtml(String name) {
        String greeting = (name == null || name.isBlank()) ? "Hello" : "Dear " + name.split(" ")[0];
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;background:#020E1A;color:#fff;border-radius:12px;overflow:hidden;">
              <div style="background:#C9A84C;padding:24px;text-align:center;">
                <h1 style="margin:0;color:#020E1A;font-size:24px;">TransitHub</h1>
                <p style="margin:6px 0 0;color:#020E1A;font-size:13px;">Intercity Bus Ticketing</p>
              </div>
              <div style="padding:24px;">
                <p style="margin:0 0 16px;font-size:16px;color:#fff;">%s,</p>
                <p style="margin:0 0 16px;font-size:14px;color:#a0aec0;line-height:1.6;">
                  Thank you for creating a TransitHub account. Your account has been set up
                  successfully. You can now book intercity bus tickets across Ghana, reserve your
                  seat, pay securely with Mobile Money, and receive your ticket as a QR code for
                  boarding.
                </p>
                <div style="background:#1B3A6B;border-radius:8px;padding:16px;margin:20px 0;">
                  <p style="margin:0 0 10px;font-size:13px;color:#C9A84C;font-weight:bold;">Getting started</p>
                  <p style="margin:0 0 6px;font-size:13px;color:#fff;">1. Search for a route, such as Kumasi to Accra.</p>
                  <p style="margin:0 0 6px;font-size:13px;color:#fff;">2. Select your seat and complete payment.</p>
                  <p style="margin:0;font-size:13px;color:#fff;">3. Present your QR code at the station gate.</p>
                </div>
                <p style="margin:0 0 16px;font-size:13px;color:#a0aec0;line-height:1.6;">
                  If you did not create this account, please disregard this email.
                </p>
                <p style="margin:0;font-size:13px;color:#a0aec0;">
                  Regards,<br>The TransitHub Team
                </p>
              </div>
            </div>
            """.formatted(greeting);
    }

    private String buildCodeHtml(String name, String code) {
        String greeting = (name == null || name.isBlank()) ? "Hi there" : "Hi " + name.split(" ")[0];
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;background:#020E1A;color:#fff;border-radius:12px;overflow:hidden;">
              <div style="background:#C9A84C;padding:20px;text-align:center;">
                <h1 style="margin:0;color:#020E1A;font-size:22px;">TransitHub</h1>
                <p style="margin:4px 0 0;color:#020E1A;font-size:13px;">Verify your email</p>
              </div>
              <div style="padding:24px;">
                <p style="margin:0 0 16px;font-size:15px;color:#fff;">%s,</p>
                <p style="margin:0 0 20px;font-size:14px;color:#a0aec0;">Enter this code in the app to finish creating your account:</p>
                <div style="background:#1B3A6B;border-radius:8px;padding:20px;text-align:center;margin-bottom:20px;">
                  <span style="font-size:34px;letter-spacing:10px;color:#C9A84C;font-weight:bold;font-family:monospace;">%s</span>
                </div>
                <p style="margin:0 0 8px;font-size:13px;color:#a0aec0;">This code expires in 15 minutes.</p>
                <p style="margin:0;font-size:13px;color:#a0aec0;">If you didn't sign up for TransitHub, you can ignore this email.</p>
                <div style="border-top:1px dashed #1B3A6B;margin:20px 0;"></div>
                <p style="color:#a0aec0;font-size:12px;text-align:center;margin:0;">Safe travels from the TransitHub team! 🚌</p>
              </div>
            </div>
            """.formatted(greeting, code);
    }

    private String buildHtml(String from, String to, String dep, String seat, String amount, String ref) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;background:#020E1A;color:#fff;border-radius:12px;overflow:hidden;">
              <div style="background:#C9A84C;padding:20px;text-align:center;">
                <h1 style="margin:0;color:#020E1A;font-size:22px;">TransitHub</h1>
                <p style="margin:4px 0 0;color:#020E1A;font-size:13px;">Booking Confirmed ✓</p>
              </div>
              <div style="padding:24px;">
                <div style="background:#1B3A6B;border-radius:8px;padding:16px;margin-bottom:16px;text-align:center;">
                  <p style="margin:0 0 4px;font-size:13px;color:#a0aec0;">Route</p>
                  <h2 style="margin:0;font-size:20px;color:#fff;">%s → %s</h2>
                </div>
                <table style="width:100%%;border-collapse:collapse;">
                  <tr><td style="padding:8px 0;color:#a0aec0;font-size:13px;">Departure</td><td style="padding:8px 0;text-align:right;color:#fff;font-size:13px;">%s</td></tr>
                  <tr><td style="padding:8px 0;color:#a0aec0;font-size:13px;">Seat</td><td style="padding:8px 0;text-align:right;color:#fff;font-size:13px;">%s</td></tr>
                  <tr><td style="padding:8px 0;color:#a0aec0;font-size:13px;">Amount Paid</td><td style="padding:8px 0;text-align:right;color:#C9A84C;font-size:14px;font-weight:bold;">GHS %s</td></tr>
                  <tr><td style="padding:8px 0;color:#a0aec0;font-size:13px;">Booking Ref</td><td style="padding:8px 0;text-align:right;color:#fff;font-size:13px;font-family:monospace;">THB-%s</td></tr>
                </table>
                <div style="border-top:1px dashed #1B3A6B;margin:16px 0;"></div>
                <p style="color:#a0aec0;font-size:12px;text-align:center;margin:0;">Show your QR code in the app at the station gate.<br>Safe travels from the TransitHub team! 🚌</p>
              </div>
            </div>
            """.formatted(from, to, dep, seat, amount, ref);
    }
}
