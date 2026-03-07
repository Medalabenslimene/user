package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - MiNoLingo");
            helper.setText(buildPasswordResetBody(resetLink), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    public void sendVerificationCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your MiNoLingo Verification Code");
            helper.setText(buildVerificationCodeBody(code), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification code email", e);
        }
    }

    public void sendAccountLockedEmail(String toEmail, String name) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ Security Alert - Account Temporarily Locked - MiNoLingo");
            helper.setText(buildAccountLockedBody(name), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send account locked email", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML Builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildPasswordResetBody(String resetLink) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'>");
        html.append("<head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>");
        html.append("<body style='margin:0;padding:0;background-color:#f0f2f5;font-family:\"Segoe UI\",Roboto,\"Helvetica Neue\",Arial,sans-serif;'>");

        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color:#f0f2f5;padding:40px 20px;'>");
        html.append("<tr><td align='center'>");
        html.append("<table role='presentation' width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>");

        // Header
        html.append("<tr><td style='background:linear-gradient(135deg,#6366f1 0%,#8b5cf6 50%,#a855f7 100%);padding:40px 40px 32px;text-align:center;'>");
        html.append("<h1 style='margin:0 0 8px;font-size:32px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;'>🌍 MiNoLingo</h1>");
        html.append("<p style='margin:0;font-size:14px;color:rgba(255,255,255,0.85);letter-spacing:0.5px;text-transform:uppercase;'>Password Reset Request</p>");
        html.append("</td></tr>");

        // Body
        html.append("<tr><td style='background-color:#ffffff;padding:32px 40px;text-align:center;'>");
        html.append("<div style='display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);border-radius:50%;width:64px;height:64px;line-height:64px;text-align:center;margin-bottom:16px;'>");
        html.append("<span style='font-size:32px;'>🔒</span>");
        html.append("</div>");
        html.append("<h2 style='margin:0 0 8px;font-size:24px;font-weight:700;color:#1e293b;'>Reset Your Password</h2>");
        html.append("<p style='margin:0 0 24px;font-size:15px;color:#64748b;line-height:1.6;'>You requested a password reset for your <strong style='color:#1e293b;'>MiNoLingo</strong> account.<br>This link is valid for <strong>1 hour</strong>.</p>");
        html.append("<a href='").append(resetLink).append("' style='display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#ffffff;text-decoration:none;padding:14px 40px;border-radius:10px;font-size:15px;font-weight:600;letter-spacing:0.3px;'>Reset My Password →</a>");
        html.append("<p style='margin:24px 0 0;font-size:13px;color:#94a3b8;'>If you did not request this, you can safely ignore this email.</p>");
        html.append("</td></tr>");

        // Footer
        html.append("<tr><td style='background-color:#ffffff;padding:24px 40px 32px;text-align:center;border-top:1px solid #e2e8f0;'>");
        html.append("<p style='margin:0;font-size:13px;color:#94a3b8;'>Thank you for using <strong style='color:#6366f1;'>MiNoLingo</strong></p>");
        html.append("<p style='margin:8px 0 0;font-size:11px;color:#cbd5e1;'>© 2026 MiNoLingo. All rights reserved.</p>");
        html.append("</td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private String buildVerificationCodeBody(String code) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'>");
        html.append("<head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>");
        html.append("<body style='margin:0;padding:0;background-color:#f0f2f5;font-family:\"Segoe UI\",Roboto,\"Helvetica Neue\",Arial,sans-serif;'>");

        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color:#f0f2f5;padding:40px 20px;'>");
        html.append("<tr><td align='center'>");
        html.append("<table role='presentation' width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>");

        // Header
        html.append("<tr><td style='background:linear-gradient(135deg,#6366f1 0%,#8b5cf6 50%,#a855f7 100%);padding:40px 40px 32px;text-align:center;'>");
        html.append("<h1 style='margin:0 0 8px;font-size:32px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;'>🌍 MiNoLingo</h1>");
        html.append("<p style='margin:0;font-size:14px;color:rgba(255,255,255,0.85);letter-spacing:0.5px;text-transform:uppercase;'>Email Verification</p>");
        html.append("</td></tr>");

        // Body
        html.append("<tr><td style='background-color:#ffffff;padding:32px 40px;text-align:center;'>");
        html.append("<div style='display:inline-block;background:linear-gradient(135deg,#22c55e,#16a34a);border-radius:50%;width:64px;height:64px;line-height:64px;text-align:center;margin-bottom:16px;'>");
        html.append("<span style='font-size:32px;color:#ffffff;'>✓</span>");
        html.append("</div>");
        html.append("<h2 style='margin:0 0 8px;font-size:24px;font-weight:700;color:#1e293b;'>Welcome to MiNoLingo!</h2>");
        html.append("<p style='margin:0 0 24px;font-size:15px;color:#64748b;line-height:1.6;'>Use the verification code below to complete your registration.</p>");

        // Code card
        html.append("<div style='background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:24px 40px;display:inline-block;margin-bottom:24px;'>");
        html.append("<p style='margin:0 0 8px;font-size:13px;color:#94a3b8;text-transform:uppercase;letter-spacing:0.5px;'>Your Verification Code</p>");
        html.append("<p style='margin:0;font-size:40px;font-weight:800;letter-spacing:12px;color:#6366f1;'>").append(escapeHtml(code)).append("</p>");
        html.append("</div>");

        html.append("<p style='margin:0;font-size:13px;color:#94a3b8;'>This code expires in <strong>10 minutes</strong>.<br>If you did not create an account, you can safely ignore this email.</p>");
        html.append("</td></tr>");

        // Footer
        html.append("<tr><td style='background-color:#ffffff;padding:24px 40px 32px;text-align:center;border-top:1px solid #e2e8f0;'>");
        html.append("<p style='margin:0;font-size:13px;color:#94a3b8;'>Thank you for choosing <strong style='color:#6366f1;'>MiNoLingo</strong></p>");
        html.append("<p style='margin:8px 0 0;font-size:11px;color:#cbd5e1;'>© 2026 MiNoLingo. All rights reserved.</p>");
        html.append("</td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private String buildAccountLockedBody(String name) {
        String safeName = escapeHtml(name != null ? name : "there");
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'>");
        html.append("<head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>");
        html.append("<body style='margin:0;padding:0;background-color:#f0f2f5;font-family:\"Segoe UI\",Roboto,\"Helvetica Neue\",Arial,sans-serif;'>");

        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color:#f0f2f5;padding:40px 20px;'>");
        html.append("<tr><td align='center'>");
        html.append("<table role='presentation' width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>");

        // Header — red/orange gradient for security alert
        html.append("<tr><td style='background:linear-gradient(135deg,#f59e0b 0%,#ef4444 100%);padding:40px 40px 32px;text-align:center;'>");
        html.append("<h1 style='margin:0 0 8px;font-size:32px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;'>🌍 MiNoLingo</h1>");
        html.append("<p style='margin:0;font-size:14px;color:rgba(255,255,255,0.85);letter-spacing:0.5px;text-transform:uppercase;'>Security Alert</p>");
        html.append("</td></tr>");

        // Body
        html.append("<tr><td style='background-color:#ffffff;padding:32px 40px;text-align:center;'>");
        html.append("<div style='display:inline-block;background:linear-gradient(135deg,#f59e0b,#ef4444);border-radius:50%;width:64px;height:64px;line-height:64px;text-align:center;margin-bottom:16px;'>");
        html.append("<span style='font-size:32px;'>⚠️</span>");
        html.append("</div>");
        html.append("<h2 style='margin:0 0 8px;font-size:24px;font-weight:700;color:#1e293b;'>Account Temporarily Locked</h2>");
        html.append("<p style='margin:0 0 24px;font-size:15px;color:#64748b;line-height:1.6;'>Hello <strong style='color:#1e293b;'>").append(safeName).append("</strong>,<br>we detected <strong>3 consecutive failed login attempts</strong> on your MiNoLingo account.</p>");

        // Info card
        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color:#fef9f0;border:1px solid #fde68a;border-radius:12px;margin-bottom:24px;'>");
        html.append("<tr><td style='padding:20px 24px;text-align:left;'>");
        html.append("<p style='margin:0 0 8px;font-size:14px;color:#92400e;font-weight:700;'>🕐 Your account is locked for 5 minutes</p>");
        html.append("<p style='margin:0;font-size:14px;color:#92400e;line-height:1.6;'>If this was you, simply wait and try again.<br>If this was <strong>NOT you</strong>, we recommend:</p>");
        html.append("<ul style='margin:8px 0 0;padding-left:20px;font-size:14px;color:#92400e;line-height:1.8;'>");
        html.append("<li>Change your password immediately after logging in</li>");
        html.append("<li>Make sure your email account is secure</li>");
        html.append("</ul>");
        html.append("</td></tr></table>");

        // Support contact card
        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color:#f0f9ff;border:1px solid #bae6fd;border-radius:12px;margin-bottom:24px;'>");
        html.append("<tr><td style='padding:20px 24px;text-align:left;'>");
        html.append("<p style='margin:0 0 8px;font-size:14px;color:#0369a1;font-weight:700;'>&#x1F4AC; Need help? Contact our support team</p>");
        html.append("<p style='margin:0 0 12px;font-size:14px;color:#0369a1;line-height:1.6;'>If you believe this was a mistake or you need assistance accessing your account, our team is here to help.</p>");
        html.append("<a href='mailto:mino.support@minolingo.online' style='display:inline-block;background:linear-gradient(135deg,#38a9f3,#0369a1);color:#ffffff;text-decoration:none;padding:10px 24px;border-radius:8px;font-size:14px;font-weight:600;'>&#x1F4E7; Contact Support</a>");
        html.append("</td></tr></table>");

        html.append("<p style='margin:0;font-size:13px;color:#94a3b8;'>Stay safe &#x2014; the MiNoLingo Security Team &#x1F6E1;</p>");
        html.append("</td></tr>");

        // Footer
        html.append("<tr><td style='background-color:#ffffff;padding:24px 40px 32px;text-align:center;border-top:1px solid #e2e8f0;'>");
        html.append("<p style='margin:0;font-size:13px;color:#94a3b8;'>Thank you for using <strong style='color:#6366f1;'>MiNoLingo</strong></p>");
        html.append("<p style='margin:8px 0 0;font-size:12px;color:#94a3b8;'>Questions? Reach us at <a href='mailto:mino.support@minolingo.online' style='color:#38a9f3;text-decoration:none;font-weight:600;'>mino.support@minolingo.online</a></p>");
        html.append("<p style='margin:8px 0 0;font-size:11px;color:#cbd5e1;'>&#169; 2026 MiNoLingo. All rights reserved.</p>");
        html.append("</td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}