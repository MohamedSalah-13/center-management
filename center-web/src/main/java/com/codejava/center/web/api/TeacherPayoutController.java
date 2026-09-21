package com.codejava.center.web.api;

import com.codejava.center.service.TeacherService;
import com.codejava.center.service.dto.SessionPayout;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * مستحقات المعلمين: الحصص المغلقة التي لم تُصرَف بعد، وصرفُ واحدة منها.
 *
 * <p>الصرف مالٌ يخرج من الدرج، فهو {@code POST} على حصةٍ بعينها لا على مبلغ: المبلغ
 * يُحسب في {@code TeacherService} من اتفاق المعلم وإيراد تلك الحصة، ومبلغٌ يصل في جسم
 * الطلب يعني أن من يستدعي الحافة يقرّر ما يُدفع.</p>
 *
 * <p>وحارسا الخدمة هما الحدّ: {@code processSessionPayout} ترفض حصةً ما زالت مفتوحة
 * وحصةً صُرفت من قبل، وكلاهما يصل {@code 409} - حالٌ في القاعدة لا خطأٌ فيما كُتب.</p>
 *
 * <p>و{@code enrolled} يُسلَّم بجانب {@code attendees} ولا يدخل الحساب: "حضر 12 من 30"
 * يقول عن الحصة ما لا يقوله رقم الحضور وحده، وعليه يُبنى قرارُ استمرار المجموعة أو
 * مراجعة الاتفاق. المستحقُّ يبقى محكوماً بالاتفاق وحده.</p>
 */
@RestController
@RequestMapping("/api/teacher-payouts")
@RequiredArgsConstructor
public class TeacherPayoutController {

    private final TeacherService teacherService;

    public record PayoutView(Long sessionId, String groupName, Long teacherId, String teacherName,
                             LocalDate sessionDate, long attendees, long enrolled,
                             Integer attendanceRate, String commissionType, String commissionName,
                             BigDecimal revenue, String formattedRevenue,
                             BigDecimal payout, String formattedPayout) {
    }

    /**
     * @param teacherId معلمٌ بعينه - وهو ما يبني كشف حسابه - أو الكل
     */
    @GetMapping
    public List<PayoutView> payouts(@RequestParam(required = false) Long teacherId) {
        List<SessionPayout> rows = teacherId == null
                ? teacherService.getPayableSessions()
                : teacherService.getPayableSessionsOf(teacherId);
        return rows.stream().map(TeacherPayoutController::view).toList();
    }

    /**
     * الصرف، وما يُعاد بعده هو القائمة من جديد.
     *
     * <p>الحصة المصروفة تخرج من القائمة، فإعادةُ قراءتها هي الجواب الوحيد الذي لا يترك
     * الشاشة تعرض سطراً صُرف توّاً ويُضغط ثانيةً.</p>
     */
    @PostMapping("/{sessionId}")
    public List<PayoutView> pay(@PathVariable Long sessionId,
                                @RequestParam(required = false) Long teacherId) {
        teacherService.processSessionPayout(sessionId);
        return payouts(teacherId);
    }

    private static PayoutView view(SessionPayout row) {
        return new PayoutView(row.sessionId(), row.groupName(), row.teacherId(), row.teacherName(),
                row.sessionDate(), row.attendees(), row.enrolled(), row.attendanceRate(),
                row.commissionType(), CommissionTypes.displayName(row.commissionType()),
                row.totalRevenue(), MoneyUtils.formatWithCurrency(row.totalRevenue()),
                row.payoutAmount(), MoneyUtils.formatWithCurrency(row.payoutAmount()));
    }
}
