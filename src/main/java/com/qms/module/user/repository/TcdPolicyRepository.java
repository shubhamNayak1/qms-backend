package com.qms.module.user.repository;

import com.qms.module.user.entity.TcdPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TcdPolicyRepository extends JpaRepository<TcdPolicy, Long> {

    @Query("SELECT p FROM TcdPolicy p WHERE p.isDeleted = false ORDER BY p.moduleKey ASC")
    List<TcdPolicy> findAllActive();

    Optional<TcdPolicy> findByModuleKeyAndIsDeletedFalse(String moduleKey);
}
