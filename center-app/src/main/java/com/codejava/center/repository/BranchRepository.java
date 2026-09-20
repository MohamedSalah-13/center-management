package com.codejava.center.repository;

import com.codejava.center.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByTenantIdAndActiveTrueOrderByName(Long tenantId);

    Optional<Branch> findByTenantIdAndCode(Long tenantId, String code);
}
