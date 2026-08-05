package com.codejava.center.service.notification;

import com.codejava.center.domain.enums.NotificationChannel;

import java.util.Optional;

/**
 * تنفيذ قناة إرسال واحدة.
 *
 * <p>لا يمتدّ {@link MessageSender} عن قصد: لو فعل لصار في السياق أربعة beans من نوعه
 * ووجب على كل حاقن أن يختار بينها، وهو بالضبط الغموض الذي أوقع الإصدار السابق في
 * {@code @ConditionalOnMissingBean}. المنفذ الوحيد يبقى {@link MessageSenderRouter}.</p>
 *
 * <p>الضبط يصل معاملاً لا يُقرأ في الداخل، فيبقى الصنف قابلاً للاختبار بلا قاعدة بيانات.</p>
 */
public interface ChannelSender {

    NotificationChannel channel();

    MessageSender.SendResult send(NotificationConfig config, String internationalPhone, String message);

    /**
     * ما ينقص هذه القناة لتعمل بهذا الضبط، أو {@code Optional} فارغ إن كانت جاهزة.
     * الفحص هنا لا في {@link #send}: الشاشة تعرضه قبل الإرسال، والموجّه يمنع به
     * محاولةً محكومة بالفشل بدل أن يترك المزوّد يردّ برسالة إنجليزية غامضة.
     */
    Optional<String> configurationProblem(NotificationConfig config);
}
