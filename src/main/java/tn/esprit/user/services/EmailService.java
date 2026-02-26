package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset Your Password - MiNoLingo");
        message.setText(
            "Hello,\n\n" +
            "You requested a password reset for your MiNoLingo account.\n\n" +
            "Click the link below to reset your password (valid for 1 hour):\n" +
            resetLink + "\n\n" +
            "If you did not request this, please ignore this email.\n\n" +
            "Best regards,\nMiNoLingo Team"
        );

        mailSender.send(message);
    }
    public void sendVerificationCode(String toEmail, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(toEmail);
    message.setSubject("Your MiNoLingo Verification Code");
    message.setText(
        "Hello,\n\n" +
        "Welcome to MiNoLingo!\n\n" +
        "Your verification code is:\n\n" +
        "  " + code + "\n\n" +
        "This code expires in 10 minutes.\n\n" +
        "If you did not create an account, ignore this email.\n\n" +
        "Best regards,\nMiNoLingo Team"
    );
    mailSender.send(message);
}
}