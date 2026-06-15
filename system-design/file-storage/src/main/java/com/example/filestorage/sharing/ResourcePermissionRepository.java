package com.example.filestorage.sharing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, UUID> {
    Optional<ResourcePermission> findByResourceTypeAndResourceIdAndGranteeUserId(ResourceType resourceType, UUID resourceId, UUID granteeUserId);
    List<ResourcePermission> findAllByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);
    List<ResourcePermission> findAllByGranteeUserIdAndRevokedAtIsNull(UUID granteeUserId);
}
