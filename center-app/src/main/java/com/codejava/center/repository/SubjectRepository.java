package com.codejava.center.repository;
import com.codejava.center.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findAllByOrderByNameAsc();
    Optional<Subject> findByNameKey(String nameKey);
}
