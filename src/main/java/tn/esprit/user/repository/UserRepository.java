package tn.esprit.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.user.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndPwd(String email, String pwd);

    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationCode(String verificationCode);

    @Modifying
    @Query("UPDATE User u SET u.banned = true, u.banReason = :reason, u.banDuration = :duration, u.banExpiresAt = :banExpiresAt WHERE u.id = :id")
    int banUserById(@Param("id") Long id, @Param("reason") String reason, @Param("duration") String duration, @Param("banExpiresAt") String banExpiresAt);

    @Modifying
    @Query("UPDATE User u SET u.banned = false, u.banReason = null, u.banDuration = null, u.banExpiresAt = null WHERE u.id = :id")
    int unbanUserById(@Param("id") Long id);

    boolean existsById(Long id);
}