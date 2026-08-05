package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

import java.util.Arrays;
import java.util.List;

/**
 * نوع الحدث المسجَّل في سجل المراقبة.
 *
 * <p>اسم القيمة يحمل الكيان والفعل معاً ({@code STUDENT_DELETED}) فلا حاجة لعمود
 * منفصل يحمل نوع الكيان. القيمة تُخزَّن نصاً في قاعدة البيانات ويُترجَم اسمها عند
 * العرض: <b>لا يُخزَّن في السجل أي نص مترجَم</b>، وإلا تجمّد كل حدث على لغة الواجهة
 * لحظة وقوعه فظهر سجل واحد بلغتين.</p>
 *
 * <p>الأحداث عالية التكرار مستثناة عن قصد: تسجيل الحضور وخصم رسوم الحصة يقعان مئات
 * المرات يومياً، ولهما جدولاهما ({@code attendances} و{@code transactions}) وهما بذاتهما
 * سجلّان بطابع زمني. تكرارهما هنا كان سيغرق السجل فيصير غير قابل للقراءة، وهو أسوأ من
 * غيابه.</p>
 */
public enum AuditAction {

    // --------------------------------------------------------- الأمان والصلاحيات
    LOGIN_SUCCEEDED(AuditCategory.SECURITY),
    LOGIN_FAILED(AuditCategory.SECURITY),
    LOGGED_OUT(AuditCategory.SECURITY),

    /** محاولة استدعاء عملية لا يملكها المستخدم؛ رفضها الحارس */
    ACCESS_DENIED(AuditCategory.SECURITY),

    USER_CREATED(AuditCategory.SECURITY),
    USER_UPDATED(AuditCategory.SECURITY),
    USER_DELETED(AuditCategory.SECURITY),

    // ------------------------------------------------------------------- المالية
    PAYMENT_RECORDED(AuditCategory.FINANCE),
    EXPENSE_RECORDED(AuditCategory.FINANCE),
    TEACHER_PAYOUT_PAID(AuditCategory.FINANCE),

    // ------------------------------------------------------------------ البيانات
    STUDENT_CREATED(AuditCategory.DATA),
    STUDENT_UPDATED(AuditCategory.DATA),
    STUDENT_DELETED(AuditCategory.DATA),
    STUDENT_ENROLLED(AuditCategory.DATA),

    GROUP_CREATED(AuditCategory.DATA),
    GROUP_UPDATED(AuditCategory.DATA),
    GROUP_DELETED(AuditCategory.DATA),

    TEACHER_CREATED(AuditCategory.DATA),
    TEACHER_UPDATED(AuditCategory.DATA),
    TEACHER_DELETED(AuditCategory.DATA),

    SESSION_OPENED(AuditCategory.DATA),
    SESSION_CLOSED(AuditCategory.DATA),

    // -------------------------------------------------------------------- النظام
    SETTINGS_UPDATED(AuditCategory.SYSTEM),
    BACKUP_CREATED(AuditCategory.SYSTEM),
    BACKUP_RESTORED(AuditCategory.SYSTEM),

    /**
     * تعديل قاعدة تنبيه: تفعيلها أو إيقافها أو تغيير وجهتها.
     *
     * <p>يُسجَّل لأن إيقاف تنبيه يجعل النظام يصمت عن حالة قائمة - "لم يصلني إشعار"
     * جوابه هنا - ولأن تحويل الوجهة إلى أولياء الأمور يجعل البرنامج يراسلهم باسم
     * السنتر بلا ضغطة من موظف، وهو تغيير في من يتكلم لا في شكل شاشة.</p>
     */
    ALERT_RULE_UPDATED(AuditCategory.SYSTEM);

    private final AuditCategory category;

    AuditAction(AuditCategory category) {
        this.category = category;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getDisplayName() {
        return I18n.get("auditAction." + name());
    }

    /** أحداث تصنيف واحد، لتمريرها إلى شرط {@code IN} في استعلام البحث */
    public static List<AuditAction> of(AuditCategory category) {
        return Arrays.stream(values()).filter(action -> action.category == category).toList();
    }
}
