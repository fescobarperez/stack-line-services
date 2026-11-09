package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteNotification;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.List;

@Repository
public interface QuoteNotificationRepository extends JpaRepository<QuoteNotification, Long> {

    /** Las que toca intentar ahora, las más viejas primero. */
    List<QuoteNotification> findByStatusAndNextAttemptAtLessThanEqualsOrderByIdAsc(
            String status, Instant ahora, Pageable pageable);
}
