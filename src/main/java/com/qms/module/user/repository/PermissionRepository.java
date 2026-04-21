package com.qms.module.user.repository;

import com.qms.module.user.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    Optional<Permission> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);

    List<Permission> findAllByModuleAndIsDeletedFalse(String module);

    Set<Permission> findAllByIdInAndIsDeletedFalse(Set<Long> ids);

    List<Permission> findAllByIsDeletedFalse();

    /** All permissions for a given user (via their roles) */
    @Query("""
            SELECT DISTINCT p FROM Permission p
            JOIN p.roles r
            JOIN r.users u
            WHERE u.id = :userId AND u.isDeleted = false
            """)
    Set<Permission> findAllByUserId(@Param("userId") Long userId);
}
