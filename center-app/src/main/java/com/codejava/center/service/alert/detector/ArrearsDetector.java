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
 * طلاب عليهم مستحقات تتجاوز الحدّ المضبوط.
 *
 * <p>الحدّ ليس زينة: رصيدٌ سالب بجنيه واحد - فرق تقريب أو حصة سُجّلت قبل دفعة - ليس
 * متأخراتٍ يُراسَل ولي الأمر بشأنها، ولو أُرسل لَفقدت الرسالة كل ثقلها حين يأتي دَينٌ
 * حقيقي.</p>
 *
 * <p>ويحترم {@code ledgerStartDate} كحساب الرصيد في كل مكان آخر: بدونه يظهر كل طالب
 * قديم مديناً بمبلغ وهمي لأن دفعاته السابقة لنظام الرصيد بلا خصومات مقابلة.</p>
 */
@Component
@RequiredArgsConstructor
public class ArrearsDetector implements AlertDetector {

    private final StudentRepository studentRepository;
    private final SettingsService settingsService;

    @Override
    public AlertType type() {
        return AlertType.ARREARS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDraft> detect(AlertRule rule) {
        BigDecimal minimumDue = BigDecimal.valueOf(Math.max(1, rule.thresholdOrDefault()));
        List<StudentBalance> arrears = studentRepository.findStudentsInArrears(ledgerStart());

        return arrears.stream()
                .filter(row -> row.amountDue().compareTo(minimumDue) >= 0)
                .map(row -> AlertDraft.forStudent(row.studentId(), row.studentName(), row.parentPhone(),
                        row.studentName(), MoneyUtils.format(row.amountDue())))
                .toList();
    }

    private LocalDateTime ledgerStart() {
        LocalDate start = settingsService.getSettings().getLedgerStartDate();
        return start == null ? null : start.atStartOfDay();
    }
}
