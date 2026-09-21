package com.codejava.center.web.api;

import com.codejava.center.domain.Alert;
import com.codejava.center.domain.enums.AlertCategory;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.service.alert.AlertService;
import com.codejava.center.service.dto.AlertPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * صندوق التنبيهات: قراءتُه، ومعالجةُ ما فيه، وفحصٌ فوريّ، وموعدُ الفحص اليومي.
 *
 * <p>يشارك البادئة مع {@code AlertStreamController} عن قصد: المجرى
 * ({@code /api/alerts/stream}) هو نفس الصندوق يصل حيّاً، والفصلُ بينهما في الكود لا
 * في الرابط - أحدهما يُبقي {@code SseEmitter} مفتوحاً والآخر يقرأ صفحةً وينتهي.</p>
 *
 * <p><b>والمعالجة لا تحذف.</b> "متى انتهت هذه المشكلة ومن نظر فيها" سؤالٌ يُطرح بعد
 * أسبوع، والحذف يجعل جوابه مستحيلاً؛ والعلامة لا تُنقض - وإن عادت الحالة أطلق الفحصُ
 * تنبيهاً جديداً بتاريخه. فلا {@code DELETE} هنا ولا نقضَ لعلامة.</p>
 *
 * <p>والفحصُ الفوريّ يُسجَّل وقتُه كما يُسجَّل المجدول: التعويضُ عن موعدٍ فائت يقارن
 * بآخر فحصٍ ناجح أياً كان مصدره، ولولا ذلك لأعاد المجدوِل الفحص نفسه بعد دقائق من
 * ضغطة المستخدم.</p>
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertInboxController {

    private final AlertService alertService;

    /**
     * @param text الجملة مبنيّةً على الخادم - لا شيء مترجَم يُخزَّن، فالصفُّ يحمل
     *             النوعَ و{@code args} محايدةً و{@code describe()} يؤلّفها عند العرض
     */
    public record AlertView(Long id, String type, String typeName,
                            String category, String categoryName,
                            String severity, String severityName,
                            LocalDateTime raisedAt, Long entityId, String entityLabel,
                            String text, boolean acknowledged,
                            LocalDateTime acknowledgedAt, String acknowledgedBy) {
    }

    public record AlertPageView(List<AlertView> rows, long totalMatching, boolean truncated,
                                long unacknowledged) {
    }

    public record AcknowledgeRequest(@NotEmpty List<Long> alertIds) {
    }

    public record AcknowledgedView(int acknowledged, long unacknowledged) {
    }

    public record ScanSettingsView(boolean enabled, LocalTime time, LocalDateTime lastScanAt) {
    }

    public record ScanSettingsRequest(boolean enabled,
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
    }

    /**
     * @param raised   كم تنبيهاً كتبه هذا الفحص - صفرٌ جوابٌ صحيح ويُقال كما هو،
     *                 فمن ضغط الزرّ يحتاج أن يعرف أن الفحص جرى ولم يجد شيئاً
     * @param messaged كم رسالةً خرجت إلى أولياء الأمور فعلاً
     * @param failures ما فشل منها، بجملته - رقمٌ وحده يقول "ثلاثة فشلت" ولا يقول أيُّها
     */
    public record ScanResultView(int raised, int messaged, List<String> failures,
                                 long unacknowledged) {
    }

    public record OptionView(String name, String label) {
    }

    @GetMapping
    public AlertPageView search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AlertCategory category,
            @RequestParam(required = false) AlertSeverity severity) {

        AlertPage page = alertService.search(from, to, category, severity);
        return new AlertPageView(page.rows().stream().map(AlertInboxController::view).toList(),
                page.totalMatching(), page.isTruncated(), alertService.unacknowledgedCount());
    }

    @GetMapping("/categories")
    public List<OptionView> categories() {
        return Arrays.stream(AlertCategory.values())
                .map(category -> new OptionView(category.name(), category.getDisplayName()))
                .toList();
    }

    @PostMapping("/acknowledge")
    public AcknowledgedView acknowledge(@Valid @RequestBody AcknowledgeRequest request) {
        int changed = alertService.acknowledge(request.alertIds());
        return new AcknowledgedView(changed, alertService.unacknowledgedCount());
    }

    @PostMapping("/scan")
    public ScanResultView scan() {
        var result = alertService.scanNow();
        return new ScanResultView(result.raised(), result.messaged(), result.failures(),
                alertService.unacknowledgedCount());
    }

    @GetMapping("/scan-settings")
    public ScanSettingsView scanSettings() {
        AlertService.ScanSettings settings = alertService.getScanSettings();
        return new ScanSettingsView(settings.enabled(), settings.time(), settings.lastScanAt());
    }

    /**
     * موعدُ الفحص ومفتاحُه، وهما <b>ليسا</b> جزءاً من حفظ الإعدادات.
     *
     * <p>صفُّ الإعدادات واحد وشاشتان تكتبان فيه، وكتابةُ الصفّ كاملاً من إحداهما تمحو
     * ما ضبطته الأخرى - وهو ما كان يقع فعلاً قبل {@code CenterSettingsDraft}.</p>
     */
    @PutMapping("/scan-settings")
    public ScanSettingsView saveScanSettings(@RequestBody ScanSettingsRequest request) {
        alertService.saveScanSettings(request.enabled(), request.time());
        return scanSettings();
    }

    private static AlertView view(Alert alert) {
        return new AlertView(alert.getId(),
                alert.getType() == null ? null : alert.getType().name(),
                alert.getType() == null ? null : alert.getType().getDisplayName(),
                alert.getType() == null || alert.getType().getCategory() == null ? null
                        : alert.getType().getCategory().name(),
                alert.getType() == null || alert.getType().getCategory() == null ? null
                        : alert.getType().getCategory().getDisplayName(),
                alert.getSeverity() == null ? null : alert.getSeverity().name(),
                alert.getSeverity() == null ? null : alert.getSeverity().getDisplayName(),
                alert.getRaisedAt(), alert.getEntityId(), alert.getEntityLabel(),
                alert.describe(), alert.isAcknowledged(),
                alert.getAcknowledgedAt(), alert.getAcknowledgedBy());
    }
}
