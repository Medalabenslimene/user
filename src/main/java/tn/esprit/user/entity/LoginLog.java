package tn.esprit.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_logs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email")
    private String email;

    @Column(name = "login_method")
    private String loginMethod; // PASSWORD | FACE | GOOGLE

    @Column(name = "success")
    private Boolean success;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "login_time")
    private LocalDateTime loginTime;
}
