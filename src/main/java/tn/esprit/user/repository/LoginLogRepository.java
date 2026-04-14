package tn.esprit.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.user.entity.LoginLog;

import java.util.List;

@Repository
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    List<LoginLog> findAllByOrderByLoginTimeDesc();

    List<LoginLog> findByUserIdOrderByLoginTimeDesc(Long userId);
}
