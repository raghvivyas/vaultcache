package com.vaultcache.repository;

import com.vaultcache.entity.RateLimitConfigEntity;
import com.vaultcache.model.LimitKeyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfigEntity, Long> {

    Optional<RateLimitConfigEntity> findBySubjectAndActiveTrue(String subject);

    List<RateLimitConfigEntity> findByKeyTypeAndActiveTrue(LimitKeyType keyType);

    List<RateLimitConfigEntity> findAllByOrderByCreatedAtDesc();

    boolean existsBySubject(String subject);
}
