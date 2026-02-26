package tn.esprit.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.user.entity.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByEmail(String email);
}
