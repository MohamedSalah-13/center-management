package com.codejava.center.service.alert;

import com.codejava.center.domain.Alert;
import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.repository.NotificationLogRepository;
import com.codejava.center.service.NotificationService;
import com.codejava.center.service.SettingsService;
import com.codejava.center.service.dto.NotificationCandidate;
import com.codejava.center.service.notification.MessageSender;
import com.codejava.center.service.notification.PhoneNumbers;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MoneyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * محرّك التنبيهات: يسأل الفاحصين، ثم يقرّر لمن يذهب ما وجدوه وبأي نصّ.
 *
 * <h2>حدود المسؤولية</h2>
 *
 * <p>{@link AlertDetector} يعرف كيف يُكتشف الحال ولا يعرف شيئاً عن الوجهة ولا اللغة.
 * {@code MessageSender} يعرف كيف تُرسل رسالة ولا يعرف لماذا. وهذا الصنف وحده هو الذي
 * يعرف الاثنين - وهو نفس التقسيم الذي يجعل إضافة تنبيه لا تمسّ قناة الإرسال، وإضافة
 * مزوّد لا تمسّ فاحصاً واحداً.</p>
 *
 * <h2>حاجزان ضدّ التكرار، لا واحد</h2>
 *
 * <ul>
 *   <li><b>الصندوق الداخلي:</b> نافذة تهدئة تُقاس من آخر تنبيه فعلي
 *       ({@code findRecentEntityIds})، فوقها قيدٌ فريد على {@code dedupe_key} في قاعدة
 *       البيانات. الأول يمنع تنبيهين متقاربين على طرفَي حدّ فترة، والثاني يمنع ثلاثة
 *       أجهزة في السنتر تصحو على نفس الموعد من كتابة نفس السطر ثلاثاً. أيٌّ منهما وحده
 *       يترك ثغرة.</li>
 *   <li><b>رسائل أولياء الأمور:</b> {@code notification_logs} وحده، وهو <b>لا يُكتب إلا
 *       بعد نجاح الإرسال</b>. ولذلك لا يصلح سطر الصندوق حاجزاً للرسائل: هو يُكتب لحظة
 *       اكتشاف الحال، فلو اعتُمد عليه لَامتنع النظام عن إعادة المحاولة بعد رسالة فشلت،
 *       ظانّاً أن ولي الأمر أُبلغ.</li>
 * </ul>
 *
 * <h2>لا حارس صلاحية هنا</h2>
 *
 * <p>عن قصد، وللسبب نفسه في {@code BackupService.executeBackup}: المجدوِل ينادي على
 * {@link #scanAll()} من خيط بلا جلسة مستخدم، فأي {@code @RequiresRole} يعني أن كل فحص
 * ليلي يُرفض. الحارس موضعُه {@code AlertService}، أي على من يغيّر القواعد ومن يقرأ
 * الصندوق - لا على الآلة وهي تنفّذ ما تقرّر من قبل.</p>
 */
@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final Map<AlertType, AlertDetector> detectors = new EnumMap<>(AlertType.class);
    private final AlertRuleRegistry ruleRegistry;
    private final AlertWriter alertWriter;
    private final AlertRepository alertRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final ApplicationEventPublisher eventPublisher;

    public AlertEngine(List<AlertDetector> detectorBeans,
                       AlertRuleRegistry ruleRegistry,
                       AlertWriter alertWriter,
                       AlertRepository alertRepository,
                       NotificationLogRepository notificationLogRepository,
                       NotificationService notificationService,
                       SettingsService settingsService,
                       ApplicationEventPublisher eventPublisher) {
        detectorBeans.forEach(detector -> detectors.put(detector.type(), detector));
        this.ruleRegistry = ruleRegistry;
        this.alertWriter = alertWriter;
        this.alertRepository = alertRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * فحصٌ كامل لكل القواعد المفعَّلة التي لها فاحص.
     *
     * <p>الأنواع الحَدَثية ({@code BACKUP_FAILED}، {@code PAYMENT_RECEIPT}) بلا فاحص
     * عن قصد: وقت وقوعها معلوم بالضبط ولا يُستنتج من قاعدة البيانات، ونصّها يحمل
     * تفصيلاً لا يبقى بعدها (رسالة الأداة، قيمة الدفعة). تصل عبر {@link #raise}.</p>
     *
     * <p>لا يُرمى استثناء من فاحص واحد إلى بقية الفحص: قاعدة معطوبة يجب أن تظهر سطراً
     * في الحصيلة لا أن تُسقط أحد عشر تنبيهاً سليماً معها.</p>
     */
    public AlertScanResult scanAll() {
        int raised = 0;
        int messaged = 0;
        List<String> failures = new ArrayList<>();

        for (AlertRule rule : ruleRegistry.all()) {
            AlertDetector detector = detectors.get(rule.getType());
            if (detector == null || !rule.isEnabled()) {
                continue;
            }

            try {
                Outcome outcome = process(rule, detector.detect(rule));
                raised += outcome.raised();
                messaged += outcome.messaged();
                failures.addAll(outcome.failures());
            } catch (RuntimeException e) {
                log.error("فشل فحص التنبيه {}: {}", rule.getType(), e.getMessage(), e);
                failures.add(I18n.format("alerts.scanFailureLine",
                        rule.getType().getDisplayName(), messageOf(e)));
            }
        }

        return new AlertScanResult(raised, messaged, failures);
    }

    /**
     * إطلاق تنبيه حَدَثيّ فور وقوعه.
     *
     * <p><b>لا ترمي شيئاً أبداً.</b> تُستدعى من داخل معالج فشل النسخة الاحتياطية ومن
     * مستمع دفعةٍ نُفّذت وأُودعت: خطأٌ يخرج منها هناك يحلّ محلّ سبب الفشل الحقيقي في
     * الرسالة التي يراها المستخدم، أو يُظهر عملية مالية ناجحة على أنها فشلت. هي نفس
     * القاعدة التي تجعل {@code AuditService.recordFailure} تبتلع خطأ الكتابة.</p>
     */
    public void raise(AlertType type, AlertDraft draft) {
        try {
            AlertRule rule = ruleRegistry.forType(type);
            if (!rule.isEnabled()) {
                return;
            }
            process(rule, List.of(draft));
        } catch (RuntimeException e) {
            log.error("تعذّر إطلاق التنبيه {}: {}", type, e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ التنفيذ

    private Outcome process(AlertRule rule, List<AlertDraft> drafts) {
        if (drafts.isEmpty()) {
            return new Outcome(0, 0, List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        int cooldown = rule.cooldownDaysOrDefault();
        // نافذة تهدئة صفرية تعني نوعاً حَدَثياً: كل وقوع حدثٌ مستقل بذاته لا يُهدَّأ
        LocalDateTime since = now.minusDays(Math.max(0, cooldown));

        Set<Long> recentlyRaised = rule.notifiesInternally()
                ? new HashSet<>(alertRepository.findRecentEntityIds(rule.getType(), since))
                : Set.of();

        Set<Long> recentlyMessaged = rule.notifiesParents()
                ? notificationLogRepository.findNotifiedStudentIds(rule.getType(), since)
                : Set.of();

        String centerName = rule.notifiesParents() ? settingsService.getCenterName() : null;

        int raised = 0;
        int messaged = 0;
        List<String> failures = new ArrayList<>();

        for (AlertDraft draft : drafts) {
            if (rule.notifiesInternally() && !recentlyRaised.contains(draft.entityId())
                    && alertWriter.insertIfNew(toAlert(rule, draft, now, cooldown))) {
                raised++;
            }

            if (rule.notifiesParents() && !recentlyMessaged.contains(draft.entityId())) {
                MessageSender.SendResult result = messageParent(rule, draft, centerName);
                if (result.success()) {
                    messaged++;
                } else {
                    failures.add(I18n.format("notify.failureLine",
                            draft.entityLabel(), result.failureReason()));
                }
            }
        }

        // إشارة واحدة للدفعة كلها لا لكل تنبيه: قائمة متأخرات بمئتَي طالب كانت ستُطلق
        // مئتَي حدث، وكلٌّ منها يوقظ نفس القراءة الواحدة
        if (raised > 0) {
            eventPublisher.publishEvent(AlertRaisedEvent.instance());
        }

        return new Outcome(raised, messaged, failures);
    }

    private Alert toAlert(AlertRule rule, AlertDraft draft, LocalDateTime now, int cooldown) {
        return Alert.builder()
                .type(rule.getType())
                // الدرجة المضبوطة الآن تُنسخ في الصف: خفضها غداً لا يعيد كتابة تاريخ اليوم
                .severity(rule.getSeverity())
                .raisedAt(now)
                .entityId(draft.entityId())
                .entityLabel(draft.entityLabel())
                .args(String.join(Alert.ARG_SEPARATOR, draft.args()))
                .dedupeKey(dedupeKey(rule.getType(), draft, cooldown, now))
                .build();
    }

    /**
     * مفتاح منع التكرار: النوع، والكيان، ورقم فترة التهدئة الحالية.
     *
     * <p>رقم الفترة يُحسب من عدّاد الأيام منذ الحقبة مقسوماً على طول التهدئة، فيخرج
     * الرقم نفسه على كل جهاز في السنتر مهما اختلفت لحظة صحوه - وهو شرط أن يمنع القيد
     * الفريد التكرار بينها. حسابه من "آخر تنبيه" مثلاً كان سيعطي كل جهاز مفتاحاً
     * مختلفاً فلا يمنع القيد شيئاً.</p>
     *
     * <p>وتهدئةٌ بصفر تعني نوعاً حَدَثياً لا يُهدَّأ: مفتاحه عشوائي، أي أن كل وقوع
     * يُكتب. نسختان احتياطيتان تفشلان في يوم واحد واقعتان لا واحدة.</p>
     *
     * <p>{@code entityId} الغائب يدخل شرطةً لا فراغاً: الفهرس الفريد في MySQL يعتبر كل
     * {@code NULL} مغايراً لغيره، فلو تُرك لَما منع القيد شيئاً في التنبيهات التي لا
     * كيان لها - وهي أكثر ما يتكرر.</p>
     */
    private String dedupeKey(AlertType type, AlertDraft draft, int cooldownDays, LocalDateTime now) {
        String entity = draft.entityId() == null ? "-" : draft.entityId().toString();
        String window = cooldownDays <= 0
                ? UUID.randomUUID().toString().substring(0, 12)
                : Long.toString(now.toLocalDate().toEpochDay() / cooldownDays);

        return type.name() + ":" + entity + ":" + window;
    }

    /**
     * رسالة ولي الأمر.
     *
     * <p><b>اسم السنتر في الموضع {@code 0} دائماً</b>، ثم وسائط الفاحص بترتيبها. قاعدة
     * واحدة لكل قوالب أولياء الأمور، بلا استثناء: رسالة تصل هاتفاً خارجياً من رقم لا
     * يعرفه المستلم يجب أن تقول من أرسلها قبل أي شيء آخر.</p>
     *
     * <p><b>ورمز العملة في الموضع الأخير دائماً</b>، تماماً كما في {@code Alert.describe}:
     * الفاحص يصف مبلغاً رقماً بلا رمز، والرمز يُقرأ من عملة السنتر لحظة الإرسال. قالب
     * لا يذكر مالاً يتجاهله.</p>
     */
    private MessageSender.SendResult messageParent(AlertRule rule, AlertDraft draft, String centerName) {
        Object[] args = new Object[draft.args().size() + 2];
        args[0] = centerName;
        for (int i = 0; i < draft.args().size(); i++) {
            args[i + 1] = draft.args().get(i);
        }
        args[args.length - 1] = MoneyUtils.currencySymbol();

        String message = I18n.format("alert.parentMessage." + rule.getType().name(), args);
        Optional<String> international = PhoneNumbers.toInternational(draft.parentPhone());

        return notificationService.sendAutomatic(new NotificationCandidate(
                draft.entityId(), draft.entityLabel(), rule.getType(),
                draft.parentPhone() == null ? "" : draft.parentPhone(),
                international.orElse(""),
                message,
                international.isPresent(),
                false));
    }

    private String messageOf(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record Outcome(int raised, int messaged, List<String> failures) {
    }
}
