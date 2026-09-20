package com.codejava.center.repository;


import com.codejava.center.domain.CenterSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CenterSettingsRepository extends JpaRepository<CenterSettings, Long> {
}
