package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.StudentGroup;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * اشتراك الطلاب في المجموعات.
 * كان هذا المنطق موزّعاً داخل شاشة تسجيل الطلاب وتتعامل مع الـ Repository مباشرةً،
 * فكان فحص السعة والحفظ يجريان خارج أي Transaction.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final StudentGroupRepository studentGroupRepository;

    @Transactional
    public StudentGroup subscribe(Student student, CourseGroup group) {
        Optional<StudentGroup> existing = studentGroupRepository.findByStudentAndGroup(student, group);

        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalStateException(I18n.get("error.enrollment.alreadyMember"));
        }

        long currentMembers = studentGroupRepository.countByGroupAndIsActiveTrue(group);
        if (group.getMaxCapacity() != null && currentMembers >= group.getMaxCapacity()) {
            throw new IllegalStateException(
                    I18n.format("error.enrollment.groupFull", group.getMaxCapacity()));
        }

        // إعادة تفعيل اشتراك سابق بدل إنشاء صف مكرر لنفس الطالب ونفس المجموعة
        StudentGroup membership = existing.orElseGet(() -> StudentGroup.builder()
                .student(student)
                .group(group)
                .build());

        membership.setActive(true);
        membership.setJoinDate(LocalDate.now());

        return studentGroupRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public long countActiveMembers(CourseGroup group) {
        return studentGroupRepository.countByGroupAndIsActiveTrue(group);
    }

    @Transactional(readOnly = true)
    public List<StudentGroup> getActiveGroupsOf(Student student) {
        return studentGroupRepository.findByStudentAndIsActiveTrue(student);
    }
}
