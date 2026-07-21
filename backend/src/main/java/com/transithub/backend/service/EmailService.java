package com.transithub.backend.service;

import com.transithub.backend.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendReceipt(Booking booking) {
        if (fromEmail == null || fromEmail.isBlank()) return;
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

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("TransitHub Booking Confirmed – " + origin + " → " + destination);
            helper.setText(buildHtml(origin, destination, dep, String.valueOf(booking.getSeatNumber()), amount, ref), true);
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("TransitHub: Failed to send receipt email – " + e.getMessage());
        }
    }

    /**
     * Unlike sendReceipt, this one throws on failure — if the code never
     * reaches the inbox the user can't finish signing up, so the caller has to
     * know rather than leave them stuck on the verify screen.
     */
    public void sendVerificationCode(String to, String name, String code) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Mail is not configured (MAIL_USERNAME / MAIL_PASSWORD)");
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(code + " is your TransitHub verification code");
            helper.setText(buildCodeHtml(name, code), true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("Could not send verification email: " + e.getMessage(), e);
        }
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
