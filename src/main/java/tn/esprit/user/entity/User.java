package tn.esprit.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;



@Entity
@Table(name = "users_info")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //user id 

    @Column(name = "name")
    private String name;

    @Column(name = "username")
    private String username;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "emailverified")
    private Boolean emailVerified = false;

    @Column(name = "verificationcode")
    private String verificationCode;

    @Column(name = "verificationcodeexpiry")
    private LocalDateTime verificationCodeExpiry;

    @Column(name = "pwd")
    private String pwd;

    @Column(name = "numtel")
    private String numTel;

    @Column(name = "datenaiss")
    private LocalDate dateNaiss;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "inscriptionok")
    private boolean inscriptionOk;

    @Column(name = "posterforum")
    private boolean posterForum;

    @Column(name = "avatar")
    private String avatar;

    // -------- TUTEUR fields --------
    @Column(name = "cin")
    private String CIN;

    @Column(name = "yearsofexperience")
    private Integer yearsOfExperience;

    @Column(name = "specialization")
    private String specialization;

    // -------- ADMIN fields --------
    @Column(name = "departement")
    private String departement;

    @Column(name = "admincin")
    private String adminCIN;

    // -------- ETUDIANT fields --------
    @Column(name = "level")
    private String level;

    @Column(name = "xp")
    private Integer xp;

    @Column(name = "streak")
    private Integer streak;

    @Column(name = "coins")
    private Integer coins;

    @Column(name = "language")
    private String language;

    @Column(name = "joindate")
    private LocalDate joinDate;

    @Column(name = "bio", length = 1000)
    private String bio;

    // -------- BAN fields --------
    @Column(name = "banned")
    private Boolean banned = false;

    @Column(name = "banreason")
    private String banReason;

    @Column(name = "banduration")
    private String banDuration;

    @Column(name = "banexpiresat")
    private String banExpiresAt;
// -------- FACE RECOGNITION fields --------
@Column(name = "faceregistered")
private Boolean faceRegistered = false;

@Column(name = "faceimageurl")
private String faceImageUrl;

// -------- LOGIN ATTEMPTS fields --------
@Column(name = "failedattempts")
private Integer failedAttempts = 0;

@Column(name = "lockeduntil")
private LocalDateTime lockedUntil;

// -------- SESSION fields --------
@Column(name = "sessiontoken")
private String sessionToken;
}