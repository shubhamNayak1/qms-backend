package com.qms.module.user.repository;

import com.qms.module.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);

    List<Role> findAllByIsDeletedFalse();

    Set<Role> findAllByIdInAndIsDeletedFalse(Set<Long> ids);

    /** Count how many non-deleted users are assigned to this role */
    @Query("""
            SELECT COUNT(u) FROM User u
            JOIN u.roles r
            WHERE r.id = :roleId AND u.isDeleted = false
            """)
    long countUsersByRoleId(@Param("roleId") Long roleId);
}
