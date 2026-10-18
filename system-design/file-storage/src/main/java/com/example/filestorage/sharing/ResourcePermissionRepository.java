package com.example.filestorage.sharing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, UUID> {
    Optional<ResourcePermission> findByResourceTypeAndResourceIdAndGranteeUserId(ResourceType resourceType, UUID resourceId, UUID granteeUserId);
    List<ResourcePermission> findAllByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);
    List<ResourcePermission> findAllByGranteeUserIdAndRevokedAtIsNull(UUID granteeUserId);

    @Query(value = """
            WITH RECURSIVE folder_chain(id) AS (
                SELECT parent_folder_id FROM file_metadata WHERE id = :fileId
                UNION
                SELECT folder.parent_folder_id FROM folders folder
                JOIN folder_chain chain ON folder.id = chain.id
                WHERE chain.id IS NOT NULL
            )
            SELECT DISTINCT permission.grantee_user_id
            FROM resource_permissions permission
            WHERE permission.revoked_at IS NULL
              AND (permission.expires_at IS NULL OR permission.expires_at > CURRENT_TIMESTAMP)
              AND ((permission.resource_type = 'FILE' AND permission.resource_id = :fileId)
                OR (permission.resource_type = 'FOLDER' AND permission.resource_id IN (SELECT id FROM folder_chain)))
            """, nativeQuery = true)
    List<UUID> findActiveAudienceForFile(@Param("fileId") UUID fileId);

    @Query(value = """
            WITH RECURSIVE folder_chain(id) AS (
                SELECT CAST(:folderId AS UUID)
                UNION
                SELECT folder.parent_folder_id FROM folders folder
                JOIN folder_chain chain ON folder.id = chain.id
                WHERE folder.parent_folder_id IS NOT NULL
            )
            SELECT DISTINCT permission.grantee_user_id
            FROM resource_permissions permission
            WHERE permission.revoked_at IS NULL
              AND (permission.expires_at IS NULL OR permission.expires_at > CURRENT_TIMESTAMP)
              AND permission.resource_type = 'FOLDER'
              AND permission.resource_id IN (SELECT id FROM folder_chain)
            """, nativeQuery = true)
    List<UUID> findActiveAudienceForFolder(@Param("folderId") UUID folderId);
}
