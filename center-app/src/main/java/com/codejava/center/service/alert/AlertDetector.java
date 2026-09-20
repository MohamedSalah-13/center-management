package com.codejava.center.service.alert;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;

import java.util.List;

/**
 * فاحصُ نوعٍ واحد من التنبيهات.
 *
 * <p><b>إضافة تنبيه جديد = bean جديد يطبّق هذه الواجهة، وقيمة جديدة في
 * {@link AlertType}، ومفاتيح نصّ. لا شيء غير ذلك.</b> {@link AlertEngine} يحقن
 * {@code List<AlertDetector>} فيلتقط الجديد من السياق وحده، ولا يوجد في النظام مكان
 * ثانٍ يجب ألا يُنسى تحديثه - وهو الشرط العملي لكون النظام قابلاً للتوسّع.</p>
 *
 * <p>الحدود والفترات تصل معاملاً في {@link AlertRule} ولا تُقرأ داخل الفاحص، للسبب
 * نفسه الذي جعل {@code ChannelSender} يستقبل {@code NotificationConfig}: قراءتها في
 * الداخل تعني استعلاماً إضافياً لكل فاحص في كل فحص، وتجعل اختبار الفاحص مستحيلاً بلا
 * سياق Spring كامل.</p>
 *
 * <p>التطبيقات تُعلَّم بـ {@code @Transactional(readOnly = true)}: الفحص قراءة بحتة،
 * والكتابة كلها في المحرّك حيث يمكن ضبط حدود المعاملة بدقّة.</p>
 */
public interface AlertDetector {

    AlertType type();

    /**
     * الحالات المستحقة للتنبيه الآن بحسب هذه القاعدة.
     *
     * <p>لا يفحص الفاحص هل القاعدة مفعَّلة ولا هل سبق التنبيه: الأولى يقرّرها المحرّك
     * قبل أن ينادي، والثانية قيدٌ في قاعدة البيانات. تكرار أيٍّ منهما في اثني عشر
     * فاحصاً يعني اثنتي عشرة فرصة لكتابته خطأً.</p>
     */
    List<AlertDraft> detect(AlertRule rule);
}
