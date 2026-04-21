package com.qms.module.qms.changecontrol.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.changecontrol.entity.ChangeControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ChangeControlRepository extends JpaRepository<ChangeControl, Long>, JpaSpecificationExecutor<ChangeControl> {

    Optional<ChangeControl> findByIdAndIsDeletedFalse(Long id);
    Optional<ChangeControl> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);

    @Query("SELECT COUNT(c) FROM ChangeControl c WHERE c.isDeleted = false AND c.dueDate < :today AND c.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);
}
