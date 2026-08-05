package com.codejava.center.service;

import java.math.BigDecimal;

/**
 * دفعةٌ حُصّلت من طالب على الخزينة.
 *
 * <p>يُنشر داخل معاملة التحصيل ولا يُستهلك إلا بعد إيداعها
 * ({@code @TransactionalEventListener}). الترتيب هنا هو كل شيء: رسالةٌ تصل ولي الأمر
 * بأن دفعته وصلت، عن معاملة تراجعت بعد ذلك، لا يمكن سحبها.</p>
 *
 * <p>بيانات الطالب تُنسخ في الحدث ولا يُمرَّر الكيان: المستهلك يعمل بعد انتهاء
 * المعاملة والجلسة معها، فأي حقل كسول فيه يرمي {@code LazyInitializationException}
 * على خيط آخر حيث لا يراه أحد.</p>
 */
public record StudentPaymentRecordedEvent(
        Long studentId,
        String studentName,
        String parentPhone,
        BigDecimal amount
) {
}
