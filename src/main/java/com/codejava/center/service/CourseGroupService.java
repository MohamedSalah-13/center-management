package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseGroupService {

    private final CourseGroupRepository courseGroupRepository;

    @Transactional
    @RequiresRole(Role.ADMIN)
    public CourseGroup saveGroup(CourseGroup group) {
        if (group.getMaxCapacity() == null || group.getMaxCapacity() <= 0) {
            throw new IllegalArgumentException(I18n.get("error.group.capacityPositive"));
        }
        if (group.getSessionPrice() == null || group.getSessionPrice().signum() < 0) {
            throw new IllegalArgumentException(I18n.get("error.group.priceNegative"));
        }
        group.setSessionPrice(MoneyUtils.normalize(group.getSessionPrice()));
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
    @RequiresRole(Role.ADMIN)
    public void deleteGroup(Long groupId) {
        // يمكنك هنا إضافة تحقق للـ Constraints (مثلاً هل يوجد طلاب مسجلين في المجموعة؟)
        // قبل السماح بالحذف لتجنب الـ DataIntegrityViolationException
        courseGroupRepository.deleteById(groupId);
    }

}