package com.qms.module.org.repository;

import com.qms.module.org.entity.Department;
import com.qms.module.org.enums.DepartmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByIdAndIsDeletedFalse(Long id);

    Optional<Department> findByCodeAndIsDeletedFalse(String code);

    List<Department> findAllByIsDeletedFalseOrderByNameAsc();

    List<Department> findAllByDeptTypeAndIsDeletedFalse(DepartmentType deptType);

    List<Department> findAllByParentIdAndIsDeletedFalseOrderByNameAsc(Long parentId);

    List<Department> findAllByParentIdIsNullAndIsDeletedFalseOrderByNameAsc();

    boolean existsByCodeAndIsDeletedFalse(String code);
}
