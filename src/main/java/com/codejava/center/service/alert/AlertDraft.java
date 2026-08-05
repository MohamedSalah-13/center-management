package com.codejava.center.service.alert;

import java.util.List;

/**
 * حالة اكتشفها فاحص، قبل أن يُقرَّر ما يُفعل بها.
 *
 * <p><b>الفاحص يصف واقعة، ولا يكتب جملة ولا يختار وجهة.</b> هو نفس الفصل الذي يقوم
 * عليه {@code NotificationService} مع {@code MessageSender}: من يعرف كيف يُسأل
 * قاعدة البيانات لا ينبغي أن يعرف بأي لغة تُعرض النتيجة ولا إلى أي هاتف تذهب. لو
 * أعاد الفاحص جملةً جاهزة لَتجمّدت على لغة الجهاز الذي فحص، ولَاحتاج كل فاحص أن
 * يعرف قناة الإرسال.</p>
 *
 * <p>{@link #args} وسائط محايدة لغوياً بالترتيب: اسم، عدد، مبلغ بلا رمز عملة. تُركَّب
 * في {@code alert.message.<TYPE>} كما هي، وفي {@code alert.parentMessage.<TYPE>}
 * بعد أن يُقحم المحرّك <b>اسم السنتر في الموضع {@code 0}</b> - قاعدة ثابتة لكل قوالب
 * أولياء الأمور، لأن رسالة تصل هاتفاً خارجياً يجب أن تقول من أرسلها قبل كل شيء.</p>
 *
 * @param entityId    معرّف الطالب أو المجموعة أو الحصة، أو {@code null} لواقعة لا كيان لها
 * @param entityLabel اسمه وقت الاكتشاف، حتى يبقى السطر مقروءاً بعد حذف الصف
 * @param args        وسائط نصّ الرسالة بالترتيب
 * @param parentPhone هاتف ولي الأمر كما هو مخزَّن، أو {@code null} لواقعة لا تُرسل خارجاً
 */
public record AlertDraft(Long entityId, String entityLabel, List<String> args, String parentPhone) {

    /** واقعة داخلية بحتة: لا هاتف ولا إرسال */
    public static AlertDraft internal(Long entityId, String entityLabel, String... args) {
        return new AlertDraft(entityId, entityLabel, List.of(args), null);
    }

    /** واقعة تخصّ طالباً، ومعها هاتف وليّه إن وُجد */
    public static AlertDraft forStudent(Long studentId, String studentName, String parentPhone,
                                        String... args) {
        return new AlertDraft(studentId, studentName, List.of(args), parentPhone);
    }
}
