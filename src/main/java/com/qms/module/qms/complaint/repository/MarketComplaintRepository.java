package com.qms.module.qms.complaint.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.complaint.entity.MarketComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MarketComplaintRepository extends JpaRepository<MarketComplaint, Long>, JpaSpecificationExecutor<MarketComplaint> {

    Optional<MarketComplaint> findByIdAndIsDeletedFalse(Long id);
    Optional<MarketComplaint> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);
    long countByReportableToAuthorityTrueAndIsDeletedFalse();

    @Query("SELECT COUNT(mc) FROM MarketComplaint mc WHERE mc.isDeleted = false AND mc.dueDate < :today AND mc.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);
}
