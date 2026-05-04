package com.qms.module.org.repository;

import com.qms.module.org.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    Optional<Site> findFirstByIsDeletedFalseOrderByIdAsc();
}
