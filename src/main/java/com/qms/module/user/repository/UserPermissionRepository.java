package com.qms.module.user.repository;

import com.qms.module.user.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionRepository
        extends JpaRepository<UserPermission, UserPermission.PK> {

    List<UserPermission> findByUserId(Long userId);

    List<UserPermission> findByUserIdAndGrantType(Long userId, UserPermission.GrantType grantType);

    List<UserPermission> findByUserIdIn(List<Long> userIds);

    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.userId = :userId AND up.permissionId = :permissionId")
    void deleteByUserIdAndPermissionId(@Param("userId") Long userId,
                                       @Param("permissionId") Long permissionId);
}
