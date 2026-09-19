package com.codejava.center.repository;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByType(AlertType type);
}
