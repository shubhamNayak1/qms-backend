package com.qms.module.lms.repository;

import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.enums.ProgramStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, Long>, JpaSpecificationExecutor<TrainingProgram> {

    Optional<TrainingProgram> findByIdAndIsDeletedFalse(Long id);
    Optional<TrainingProgram> findByCodeAndIsDeletedFalse(String code);
    boolean existsByCodeAndIsDeletedFalse(String code);

    /**
     * @param search Pre-built lower-cased wildcard pattern, e.g. {@code %gmp%},
     *               or {@code null} to skip text filtering.
     *               The pattern is built by the service layer to avoid Hibernate 6
     *               binding a null String as {@code bytea}, which causes
     *               "function lower(bytea) does not exist" in PostgreSQL.
     */
    @Query("""
            SELECT p FROM TrainingProgram p
            WHERE p.isDeleted = false
              AND (:status     IS NULL OR p.status      = :status)
              AND (:category   IS NULL OR p.category    = :category)
              AND (:department IS NULL OR p.department  = :department)
              AND (:mandatory  IS NULL OR p.isMandatory = :mandatory)
              AND (:search     IS NULL
                   OR LOWER(p.title) LIKE :search
                   OR LOWER(p.code)  LIKE :search
                   OR LOWER(p.tags)  LIKE :search)
            """)
    Page<TrainingProgram> search(@Param("status")     ProgramStatus status,
                                 @Param("category")   String        category,
                                 @Param("department") String        department,
                                 @Param("mandatory")  Boolean       mandatory,
                                 @Param("search")     String        search,
                                 Pageable pageable);

    long countByStatusAndIsDeletedFalse(ProgramStatus status);
}
