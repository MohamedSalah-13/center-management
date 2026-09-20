package com.codejava.center.service.alert.detector;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.alert.AlertDetector;
import com.codejava.center.service.alert.AlertDraft;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * طلاب أوشك رصيدهم على النفاد وهو موجب بعد.
 *
 * <p>التنبيه قبل الحدث لا بعده. بوابة الحضور تمنع الطالب حين يصير رصيده غير كافٍ،
 * وذلك يقع أمام باب القاعة والحصة على وشك البدء: تذكيرٌ قبله بيوم أو يومين يحوّل موقفاً
 * محرجاً إلى دفعةٍ عادية على الخزينة.</p>
 *
 * <p>الحدّ مبلغ بالجنيه لا عدد حصص، وإن كان عدد الحصص أقرب إلى ما يفكّر به صاحب
 * السنتر: الطالب قد يشترك في مجموعتين بسعرين مختلفين، فلا يوجد "سعر حصة" واحد
 * لرصيده. المبلغ لا لبس فيه.</p>
 */
@Component
@RequiredArgsConstructor
public class LowBalanceDetector implements AlertDetector {

    private final StudentRepository studentRepository;
    private final SettingsService settingsService;

    @Override
    public AlertType type() {
        return AlertType.LOW_BALANCE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        BigDecimal ceiling = BigDecimal.valueOf(Math.max(1, rule.thresholdOrDefault()));
        List<StudentBalance> running = studentRepository.findStudentsWithLowBalance(ledgerStart(), ceiling);

        return running.stream()
                .map(row -> AlertDraft.forStudent(row.studentId(), row.studentName(), row.parentPhone(),
                        row.studentName(), MoneyUtils.format(row.balance())))
                .toList();
    }

    private LocalDateTime ledgerStart() {
        LocalDate start = settingsService.getSettings().getLedgerStartDate();
        return start == null ? null : start.atStartOfDay();
    }
}
