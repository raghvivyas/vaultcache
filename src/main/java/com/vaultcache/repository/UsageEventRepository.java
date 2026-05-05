package com.vaultcache.repository;

import com.vaultcache.entity.UsageEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEventEntity, Long> {

    long countBySubjectAndAllowedTrue(String subject);
    long countBySubjectAndAllowedFalse(String subject);

    @Query("SELECT AVG(e.redisLatencyMs) FROM UsageEventEntity e WHERE e.subject = :subject")
    BigDecimal avgLatencyBySubject(@Param("subject") String subject);

    @Query("SELECT MAX(e.occurredAt) FROM UsageEventEntity e WHERE e.subject = :subject")
    Instant lastSeenAt(@Param("subject") String subject);

    @Query("SELECT e.subject, COUNT(e) AS total FROM UsageEventEntity e " +
           "WHERE e.allowed = false GROUP BY e.subject ORDER BY total DESC")
    List<Object[]> findTopThrottledSubjects(Pageable pageable);

    @Query("SELECT AVG(e.redisLatencyMs) FROM UsageEventEntity e WHERE e.occurredAt >= :since")
    BigDecimal avgLatencySince(@Param("since") Instant since);
}
