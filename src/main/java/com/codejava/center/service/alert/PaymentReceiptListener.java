package com.codejava.center.service.alert;

import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.StudentPaymentRecordedEvent;
import com.codejava.center.service.TransactionService;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * تأكيد استلام دفعة، بعد أن تُودَع معاملتها.
 *
 * <p>هذا هو التنبيه الوحيد الذي وجهته الطبيعية ولي الأمر لا المدير، وهو الوحيد الذي
 * يقع لحظةً بعينها فلا يصلح لفحص مجدول: قيمة الدفعة والرصيد بعدها معلومان الآن، وبعد
 * ساعات يصيران استنتاجاً من صفوف قد تكون تغيّرت.</p>
 *
 * <p>{@code AFTER_COMMIT} صراحةً لا افتراضاً: رسالةٌ تصل هاتف ولي الأمر لا يمكن سحبها،
 * ومعاملةٌ تراجعت بعد إرسالها تترك السنتر وقد أكّد استلام مال لم يستلمه. وهي مضبوطة
 * على {@code fallbackExecution = false} - أي لا تعمل خارج معاملة أصلاً - حتى لا يمرّ
 * الإرسال يوم يُستدعى التحصيل من سياق بلا معاملة.</p>
 *
 * <p>ولا يرمي شيئاً: {@link AlertEngine#raise} تبتلع أخطاءها، والمستمع نفسه يعمل بعد
 * الإيداع فرميه هنا يلوّث سجلّ التطبيق بخطأ عن عملية مالية نجحت تماماً.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentReceiptListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptListener.class);

    private final AlertEngine alertEngine;
    private final TransactionService transactionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onPaymentRecorded(StudentPaymentRecordedEvent event) {
        try {
            BigDecimal balance = transactionService.getStudentBalance(event.studentId());

            alertEngine.raise(AlertType.PAYMENT_RECEIPT, AlertDraft.forStudent(
                    event.studentId(), event.studentName(), event.parentPhone(),
                    event.studentName(),
                    MoneyUtils.format(event.amount()),
                    MoneyUtils.format(balance)));
        } catch (RuntimeException e) {
            // لا نكتب اسم الطالب أو هاتف ولي الأمر في ملف دعم خارج قاعدة البيانات.
            log.error("تعذّر تجهيز تأكيد استلام الدفعة للطالب id={}", event.studentId(), e);
        }
    }
}
