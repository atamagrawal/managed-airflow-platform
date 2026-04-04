package com.airflow.platform.repository;

import com.airflow.platform.model.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByUsernameIgnoreCaseAndEnabledIsTrue(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
