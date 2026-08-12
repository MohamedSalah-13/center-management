package com.codejava.center.service;

import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.AuditCategory;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.AuditLogRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.AuditPage;
import com.codejava.center.util.MoneyUtils;
import com.codejava.center.util.UserSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * كتابة سجل المراقبة وقراءته.
 *
 * <h2>لماذا نوعان من الكتابة</h2>
 *
 * <p>أحداث <b>النجاح</b> تُكتب داخل معاملة من استدعاها ({@code REQUIRED}). فائدة ذلك
 * مزدوجة: عملية تراجعت لا تترك خلفها سطراً يقول إنها وقعت، والعكس أهمّ - لو تعذّرت
 * كتابة السطر تراجعت العملية معه. أي أن <b>ما لا يمكن تسجيله لا يحدث</b>، وهو الشرط
 * الذي يجعل غياب حدث من السجل دليلاً لا احتمالاً.</p>
 *
 * <p>أحداث <b>الفشل والرفض</b> تُكتب في معاملة مستقلة ({@code REQUIRES_NEW}). محاولة
 * الوصول المرفوضة ترمي استثناءً يتراجع معه كل ما في المعاملة الجارية، فلو شاركها
 * السطر لَمُحي معها - ولَما بقي أثر لمن حاول. وهذه بالضبط الأسطر التي يوجد السجل
 * من أجلها.</p>
 *
 * <p>ولذلك أيضاً تلتقط {@link #recordFailure} أي خطأ كتابة ولا تعيد رميه: هي تُستدعى
 * على مسار استثناء قائم، وخطؤها كان سيحلّ محلّ سبب الرفض الأصلي في الرسالة التي
 * يراها المستخدم.</p>
 *
 * <p>القراءة مقصورة على المدير: السجل يكشف حركة الخزينة كاملة ومن حاول الوصول
 * إلى ماذا.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /**
     * سقف ما تجلبه الشاشة دفعةً واحدة. السجل لا يُنظَّف ولا يُقصّ، وسنتر بعد سنتين
     * قد يحمل مئات الآلاف من الأسطر: جلبها كلها إلى جدول JavaFX يعلّق الواجهة.
     * ما زاد يُعرَف عدده من {@link AuditPage#totalMatching()} ويُبلَّغ به المستخدم.
     */
    public static final int MAX_ROWS = 1000;

    /** حدود الأعمدة النصية؛ القصّ هنا أفضل من استثناء يُفشل عملية مالية سليمة */
    private static final int LABEL_LIMIT = 150;
    private static final int DETAILS_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserSession userSession;

    // ------------------------------------------------------------------- الكتابة

    /** حدث ناجح منسوب إلى مستخدم الجلسة الحالي */
    @Transactional
    public void record(AuditAction action, Long entityId, String entityLabel) {
        record(action, entityId, entityLabel, null, null);
    }

    @Transactional
    public void record(AuditAction action, Long entityId, String entityLabel, String details) {
        record(action, entityId, entityLabel, null, details);
    }

    /** حدث مالي: المبلغ عمود مستقل حتى تستطيع الشاشة عرضه وجمعه */
    @Transactional
    public void record(AuditAction action, Long entityId, String entityLabel,
                       BigDecimal amount, String details) {
        User actor = userSession.getCurrentUser();

        write(AuditLog.builder()
                .actorUsername(actor == null ? null : actor.getUsername())
                .actorRole(actor == null ? null : actor.getRole())
                .action(action)
                .entityId(entityId)
                .entityLabel(entityLabel)
                .amount(amount == null ? null : MoneyUtils.normalize(amount))
                .details(details)
                .successful(true)
                .build());
    }

    /**
     * حدث فشل أو رُفض. يُكتب في معاملة مستقلة ولا يرمي شيئاً - راجع شرح الصف.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuditAction action, String entityLabel, String details) {
        User actor = userSession.getCurrentUser();

        try {
            write(AuditLog.builder()
                    .actorUsername(actor == null ? null : actor.getUsername())
                    .actorRole(actor == null ? null : actor.getRole())
                    .action(action)
                    .entityLabel(entityLabel)
                    .details(details)
                    .successful(false)
                    .build());
        } catch (RuntimeException e) {
            // على مسار استثناء قائم: رمي خطأ الكتابة هنا يُخفي سبب الرفض عن المستخدم
            log.error("تعذر حفظ حدث فشل في سجل المراقبة: {}", action, e);
        }
    }

    /**
     * حدث منسوب إلى مستخدم يُمرَّر صراحةً لا إلى جلسة قائمة.
     *
     * <p>الدخول والخروج لا يمكن نسبتهما إلى {@link UserSession}: عند نجاح الدخول لم
     * تُضبط الجلسة بعد، وعند فشله لا مستخدم أصلاً بل اسم مكتوب في الشاشة، وعند الخروج
     * تكون الجلسة قد أُفرغت.</p>
     *
     * <p>{@code username} يُسجَّل كما كُتب حتى لو لم يكن لأحد: محاولات دخول متكررة
     * باسم غير موجود هي نفسها ما يجب أن يراه صاحب السنتر.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(String username, Role role, AuditAction action,
                         boolean successful, String details) {
        try {
            write(AuditLog.builder()
                    .actorUsername(username)
                    .actorRole(role)
                    .action(action)
                    .details(details)
                    .successful(successful)
                    .build());
        } catch (RuntimeException e) {
            // فشل الدخول لا يجوز أن يتحول إلى خطأ نظام أمام من يحاول الدخول
            log.error("تعذر حفظ حدث منسوب صراحةً في سجل المراقبة: {}", action, e);
        }
    }

    private void write(AuditLog entry) {
        entry.setOccurredAt(LocalDateTime.now());
        entry.setEntityLabel(clip(entry.getEntityLabel(), LABEL_LIMIT));
        entry.setDetails(clip(entry.getDetails(), DETAILS_LIMIT));
        auditLogRepository.save(entry);
    }

    private String clip(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit - 1) + "…";
    }

    // ------------------------------------------------------------------- القراءة

    /**
     * أحداث فترة، مصفّاة اختيارياً بالمستخدم وبالتصنيف.
     *
     * @param actor    اسم المستخدم، أو {@code null} للجميع
     * @param category التصنيف، أو {@code null} لكل التصنيفات
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public AuditPage search(LocalDate from, LocalDate to, String actor, AuditCategory category) {
        LocalDateTime start = from.atStartOfDay();
        // بداية اليوم التالي مع مقارنة "أصغر من": تشمل آخر ثانية بكل أجزائها
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<AuditAction> actions = category == null
                ? List.of(AuditAction.values())
                : AuditAction.of(category);

        List<AuditLog> rows = auditLogRepository.search(
                start, end, blankToNull(actor), actions, PageRequest.of(0, MAX_ROWS));

        return new AuditPage(rows, auditLogRepository.countMatching(
                start, end, blankToNull(actor), actions));
    }

    /** أسماء من له أثر في السجل، لقائمة التصفية */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<String> getActors() {
        return auditLogRepository.findDistinctActors();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
