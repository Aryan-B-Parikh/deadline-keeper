package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.ExternalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalEventRepository extends JpaRepository<ExternalEvent, UUID> {

    Optional<ExternalEvent> findByUserIdAndProviderAndExternalId(UUID userId, String provider, String externalId);

    List<ExternalEvent> findByDeadlineId(UUID deadlineId);

    void deleteByUserIdAndProviderAndExternalId(UUID userId, String provider, String externalId);
}
