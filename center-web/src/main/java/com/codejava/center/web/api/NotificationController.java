package com.codejava.center.web.api;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.NotificationLog;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.service.AttendanceService;
import com.codejava.center.service.CourseGroupService;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.StudentService;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.util.I18n;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * رسائلُ أولياء الأمور: من يستحقّ رسالةً الآن، وإرسالُها.
 *
 * <p><b>والمرشَّحُ لا يصل من جسم الطلب أبداً.</b> هو يحمل الرقمَ ونصَّ الرسالة، فقبولُه
 * كما يأتي يعني أن أيَّ من يملك جلسةً يُرسل أيَّ نصّ إلى أيِّ رقم من حساب السنتر عند
 * المزوّد - وعلى فاتورته، وباسمه. فالطلبُ يقول <b>أيَّ قائمةٍ يعيد بناءها ومن فيها</b>
 * (النوع، والمجموعة والمدة للغياب، ورقم الطالب)، والخادمُ يبنيها من جديد ويأخذ منها
 * مرشَّح ذلك الطالب. نفس القاعدة التي جعلت الصرف {@code POST} على حصة لا على مبلغ.</p>
 *
 * <p>والإرسالُ ثلاثةُ نتائج لا اثنتان: نجاحٌ، وفشلٌ بسببه، و<b>تسليمٌ إلى إنسان</b> -
 * رابطٌ يفتحه موظف ثم يضغط "إرسال" في واتساب بنفسه. وهو <b>ليس نجاحاً</b>: لا يُكتب
 * في {@code notification_logs}، فالسطر هناك يعني "فُتحت محادثة وليّ الأمر بالرسالة
 * فيها". ومن فتح الرابط يقول ذلك بنفسه عبر {@code /opened} - وكتابتُه لحظةَ بناء
 * الرابط تعني سطراً عن محادثةٍ لم تُفتح، ثم حارسَ تكرارٍ يمنع إعادة المحاولة فيبقى
 * وليُّ أمرٍ بلا خبر ولا أحد يدري.</p>
 *
 * <p>ولذلك يحمل الجواب {@code link} ولا يفتحه أحد على الخادم: خادمٌ بلا شاشة، ورابطٌ
 * يُفتح في متصفحه لا يراه من طلبه.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /**
     * ما يُبنى منه قائمةُ مرشَّحين، وما يعرضه {@code /types}.
     *
     * <p>وهما واحد عن قصد: هذه القائمة و{@code switch} في {@code build} يفترقان يوماً
     * ما إن كُتبا مرتين، و{@code everySelectableTypeBuildsAList} يقف على ذلك.</p>
     */
    private static final List<AlertType> SELECTABLE = List.of(AlertType.ABSENCE, AlertType.ARREARS);

    private final NotificationService notificationService;
    private final AttendanceService attendanceService;
    private final CourseGroupService courseGroupService;
    private final StudentService studentService;

    /**
     * ما يصف قائمةً يُعاد بناؤها، لا ما يصف رسالة.
     *
     * @param groupId المجموعة، وهي لازمةٌ للغياب وحده
     */
    public record CandidateQuery(@NotNull AlertType type, Long groupId,
                                 LocalDate from, LocalDate to) {
    }

    public record SendRequest(@NotNull AlertType type, Long groupId,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @NotNull Long studentId) {
    }

    /**
     * @param sendable  الرقمُ صالحٌ ولم يُرسل له مثلُها في الفترة
     * @param statusLabel سببُ عدم الإرسال بجملته، أو "جاهز"
     */
    public record CandidateView(Long studentId, String studentName, String phone,
                                String message, boolean sendable, String statusLabel) {
    }

    /**
     * @param link رابطُ المحادثة حين تكون القناة يدويّة - يفتحه إنسان ثم يضغط إرسال
     */
    public record SendResultView(boolean success, boolean needsHandOff,
                                 String link, String failureReason) {
    }

    /**
     * @param problem ما يمنع الإرسال اليوم - مفتاحٌ غائب عن هذا الخادم مثلاً - أو
     *                {@code null}. يُسأل قبل يومٍ من الإرسال لا بعد أربعين رفضاً
     */
    public record ChannelView(boolean requiresManualConfirmation, String problem) {
    }

    public record LogView(Long id, Long studentId, String type, String typeName,
                          String phone, LocalDateTime sentAt) {
    }

    /** @param needsGroup هل يلزم اختيارُ مجموعةٍ ومدةٍ لبناء قائمة هذا النوع؟ */
    public record TypeView(String name, String label, boolean needsGroup) {
    }

    /**
     * الأنواعُ التي لها قائمةُ مرشَّحين، من الخادم لا من الصفحة.
     *
     * <p>{@code build} هو من يعرف أيُّ نوعٍ يُبنى وبماذا، وقائمةٌ ثانية مكتوبةٌ في JS
     * هي قائمةٌ تختلف عنها يوماً ما: نوعٌ يُضاف هنا ولا يُعرض هناك يبقى غيرَ مستعمَل
     * بلا أن يقول أحدٌ شيئاً، ونوعٌ يُعرض ولا يُبنى يُردّ بعد أن يختاره الموظف.</p>
     */
    @GetMapping("/types")
    public List<TypeView> types() {
        return SELECTABLE.stream()
                .map(type -> new TypeView(type.name(), type.getDisplayName(),
                        type == AlertType.ABSENCE))
                .toList();
    }

    @GetMapping("/channel")
    public ChannelView channel() {
        return new ChannelView(notificationService.channelRequiresManualConfirmation(),
                notificationService.channelProblem().orElse(null));
    }

    @GetMapping("/candidates")
    public List<CandidateView> candidates(
            @RequestParam AlertType type,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return build(new CandidateQuery(type, groupId, from, to)).stream()
                .map(NotificationController::view)
                .toList();
    }

    @PostMapping("/send")
    public SendResultView send(@Valid @RequestBody SendRequest request) {
        MessageSender.SendResult result = notificationService.send(candidateOf(request));
        return new SendResultView(result.success(), result.needsHandOff(),
                result.link(), result.failureReason());
    }

    /**
     * "فُتحت المحادثة": يقولها من فتح الرابط، بعد أن فتحه.
     *
     * <p>وهذا ما يُبقي {@code notification_logs} صادقاً. كتابةُ السطر لحظةَ بناء
     * الرابط تسجّل إشعاراً لمحادثةٍ لم تُفتح، وحارسُ التكرار يرفض بعدها إعادة
     * المحاولة - فيبقى وليُّ أمرٍ بلا خبر ولا أحد يدري.</p>
     */
    @PostMapping("/opened")
    public void opened(@Valid @RequestBody SendRequest request) {
        notificationService.recordOpened(candidateOf(request));
    }

    @GetMapping("/log")
    public List<LogView> log() {
        return notificationService.getRecentNotifications().stream()
                .map(NotificationController::logView)
                .toList();
    }

    /**
     * المرشَّحُ يُعاد بناؤه ثم يُلتقط منه صاحبُ الرقم المطلوب.
     *
     * <p>ومن لم يعد في القائمة يُردّ: الحالةُ تغيّرت بين القراءة والضغط - دفع الطالب،
     * أو أُرسلت له رسالةٌ من جهازٍ آخر - وإرسالُ رسالةٍ بُنيت على قراءةٍ قديمة يقول
     * لوليّ الأمر ما لم يعد صحيحاً.</p>
     */
    private NotificationCandidate candidateOf(SendRequest request) {
        return build(new CandidateQuery(request.type(), request.groupId(),
                request.from(), request.to())).stream()
                .filter(candidate -> request.studentId().equals(candidate.studentId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        I18n.get("error.notification.candidateGone")));
    }

    private List<NotificationCandidate> build(CandidateQuery query) {
        return switch (query.type()) {
            case ABSENCE -> {
                CourseGroup group = courseGroupService.findById(query.groupId());
                LocalDate to = query.to() == null ? LocalDate.now() : query.to();
                LocalDate from = query.from() == null ? to.minusDays(30) : query.from();
                yield notificationService.buildAbsenceNotifications(
                        attendanceService.getGroupAttendance(group, from, to));
            }
            case ARREARS -> notificationService.buildArrearsNotifications(
                    studentService.getStudentsInArrears());
            // نوعٌ ليس له قائمةُ مرشَّحين: التذكيرات والإيصالات تخرج من محرّك التنبيهات
            // نفسه، ولا شاشةَ تختار منها من يُرسَل إليه
            default -> throw new IllegalArgumentException(
                    I18n.format("error.notification.typeNotSelectable", query.type().getDisplayName()));
        };
    }

    private static CandidateView view(NotificationCandidate candidate) {
        return new CandidateView(candidate.studentId(), candidate.studentName(),
                candidate.rawPhone(), candidate.message(),
                candidate.sendable(), candidate.statusLabel());
    }

    private static LogView logView(NotificationLog row) {
        return new LogView(row.getId(),
                row.getStudent() == null ? null : row.getStudent().getId(),
                row.getType() == null ? null : row.getType().name(),
                row.getType() == null ? null : row.getType().getDisplayName(),
                row.getRecipientPhone(), row.getSentAt());
    }
}
