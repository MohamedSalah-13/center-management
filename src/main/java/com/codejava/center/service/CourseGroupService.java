package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.repository.CourseGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseGroupService {

    private final CourseGroupRepository courseGroupRepository;

    @Transactional
    public CourseGroup saveGroup(CourseGroup group) {
        if (group.getMaxCapacity() == null || group.getMaxCapacity() <= 0) {
            throw new IllegalArgumentException("سعة القاعة يجب أن تكون أكبر من صفر.");
        }
        if (group.getSessionPrice() == null || group.getSessionPrice() < 0) {
            throw new IllegalArgumentException("سعر الحصة لا يمكن أن يكون سالباً.");
        }
        return courseGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<CourseGroup> getAllGroups() {
        return courseGroupRepository.findAll();
    }
    /**
     * حذف مجموعة دراسية
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        // يمكنك هنا إضافة تحقق للـ Constraints (مثلاً هل يوجد طلاب مسجلين في المجموعة؟)
        // قبل السماح بالحذف لتجنب الـ DataIntegrityViolationException
        courseGroupRepository.deleteById(groupId);
    }

}