package com.codejava.center.service;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseGroupService {

    private final CourseGroupRepository courseGroupRepository;
    private final AuditService auditService;

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

        boolean isNew = group.getId() == null;
        CourseGroup saved = courseGroupRepository.save(group);

        // سعر الحصة يُسجَّل مع كل تعديل: هو ما يُخصم من رصيد كل طالب عند حضوره،
        // وتخفيضه ثم إعادته بعد الحصة تغيير مالي لا يترك أثراً في جدول الحركات
        auditService.record(isNew ? AuditAction.GROUP_CREATED : AuditAction.GROUP_UPDATED,
                saved.getId(), saved.getName(),
                "price=" + MoneyUtils.format(saved.getSessionPrice())
                        + "; capacity=" + saved.getMaxCapacity());

        return saved;
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
        Optional<String> name = courseGroupRepository.findById(groupId).map(CourseGroup::getName);

        courseGroupRepository.deleteById(groupId);
        auditService.record(AuditAction.GROUP_DELETED, groupId, name.orElse(null));
    }

}
