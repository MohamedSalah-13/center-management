package com.codejava.center.web.api;

import com.codejava.center.domain.Session;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.SessionService;
import com.codejava.center.service.dto.AttendanceLogRow;
import com.codejava.center.service.dto.AttendanceResult;
import com.codejava.center.util.MoneyUtils;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * بوابة الحضور على الشبكة.
 *
 * <p>القارئ الموصول بجهازٍ عند الباب يكتب الباركود في حقل ويرسله؛ والقرار كله -
 * دخولٌ أم انصراف، وهل يكفي الرصيد، وهل مرّت مهلة التكرار - في
 * {@code AttendanceService} كما هو، ولا سطر منه هنا.</p>
 *
 * <p>ولا مفتاح "وضع الانصراف" في الواجهة، للسبب نفسه المكتوب في {@code CLAUDE.md}:
 * يُترك على الانصراف فيُسجَّل من وصل توّاً خارجاً.</p>
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SessionService sessionService;

    public record ScanRequest(@NotBlank String barcode, Long sessionId) {
    }

    public record ScanView(String outcome, boolean success, String studentName, String groupName,
                           String message, String remainingBalance, LogRow row) {
    }

    public record LogRow(Long attendanceId, String studentName, String barcode, String groupName,
                         LocalDateTime timeIn, LocalDateTime timeOut,
                         String state, String stateName) {
    }

    public record OpenSession(Long id, Long groupId, String groupName) {
    }

    @PostMapping("/scan")
    public ScanView scan(@RequestBody ScanRequest request) {
        return view(attendanceService.processAttendance(request.barcode(), request.sessionId()));
    }

    @PostMapping("/{attendanceId}/checkout")
    public ScanView checkOut(@PathVariable Long attendanceId) {
        return view(attendanceService.checkOut(attendanceId));
    }

    @GetMapping("/today")
    public List<LogRow> today() {
        return attendanceService.getTodayLog().stream().map(AttendanceController::row).toList();
    }

    /** الحصص المفتوحة: الشاشة تربط نفسها بواحدة حين تخدم قاعةً بعينها */
    @GetMapping("/sessions")
    public List<OpenSession> openSessions() {
        return sessionService.getActiveSessions().stream()
                .map(session -> new OpenSession(session.getId(), groupId(session), groupName(session)))
                .toList();
    }

    private static Long groupId(Session session) {
        return session.getGroup() == null ? null : session.getGroup().getId();
    }

    private static String groupName(Session session) {
        return session.getGroup() == null ? null : session.getGroup().getName();
    }

    private static ScanView view(AttendanceResult result) {
        return new ScanView(
                result.getOutcome().name(),
                result.isSuccess(),
                result.getStudentName(),
                result.getGroupName(),
                result.getMessage(),
                result.getRemainingBalance() == null ? null
                        : MoneyUtils.formatWithCurrency(result.getRemainingBalance()),
                result.getRow() == null ? null : row(result.getRow()));
    }

    /**
     * {@code state} نصٌّ من ثلاث قيم لا خانةٌ منطقية.
     *
     * <p>{@code timeOut} فارغٌ يعني شيئين: "داخل الآن" ما دامت الحصة مفتوحة، و"لم
     * يُسجَّل انصرافه" بعد إغلاقها. وجمعُهما في قيمة واحدة يجعل كشف الأسبوع الماضي
     * يقول إن نصف السنتر ما زال في المبنى.</p>
     */
    private static LogRow row(AttendanceLogRow row) {
        return new LogRow(row.attendanceId(), row.studentName(), row.barcode(), row.groupName(),
                row.timeIn(), row.timeOut(), row.state().name(), row.state().getDisplayName());
    }
}
