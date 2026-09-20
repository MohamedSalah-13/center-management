package com.codejava.center.service.alert;

import com.codejava.center.domain.Alert;
import com.codejava.center.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * كتابة سطر التنبيه، في معاملة مستقلة.
 *
 * <p>صنف قائم بذاته لسبب واحد، وهو سبب كافٍ: <b>الاستثناء الذي نريد التقاطه هو
 * {@link DataIntegrityViolationException}، والتقاطه داخل معاملة الفحص لا ينفع.</b>
 * أول خرق للقيد الفريد يُعلّم المعاملة الجارية بـ rollback-only، فيفشل الحفظ عند
 * الإيداع مهما بدا أن الكود عالج الاستثناء - وتضيع تنبيهات الفحص كلها لأن واحداً منها
 * كان مكرّراً. {@code REQUIRES_NEW} يجعل كل محاولة إدراج منفصلة تماماً.</p>
 *
 * <p>و{@code REQUIRES_NEW} يستدعي أن يكون النداء عبر bean آخر: الاستدعاء الذاتي داخل
 * نفس الصنف لا يمرّ بالوكيل فلا تُنشأ معاملة جديدة أصلاً، وهو عطبٌ صامت تماماً.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertWriter {

    private final AlertRepository alertRepository;

    /**
     * يحفظ التنبيه ما لم يكن مفتاحه مستعملاً.
     *
     * @return {@code true} إن كُتب فعلاً، و{@code false} إن كان مكرّراً
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertIfNew(Alert alert) {
        // فحص مسبق يوفّر رحلةً فاشلة في الحالة الغالبة؛ القيد الفريد هو الضمان
        if (alertRepository.existsByDedupeKey(alert.getDedupeKey())) {
            return false;
        }

        try {
            alertRepository.saveAndFlush(alert);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // جهاز آخر في السنتر سبقنا إلى نفس الواقعة بين الفحص والإدراج.
            // ليست حالة خطأ: هذا بالضبط ما وُضع القيد ليفعله
            return false;
        }
    }
}
