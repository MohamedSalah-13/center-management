package com.codejava.center.web.api;

import com.codejava.center.domain.AuditLog;
import com.codejava.center.domain.enums.AuditCategory;
import com.codejava.center.service.AuditService;
import com.codejava.center.service.dto.AuditPage;
import com.codejava.center.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * سجلّ المراقبة: قراءةٌ فقط، وهذا ليس نقصاً في التغطية بل هو الميزة.
 *
 * <p>{@code AuditLogRepository} لا يرث {@code JpaRepository} بل {@code Repository}
 * وحده، فلا وجودَ لـ {@code delete} على جدولٍ كلُّ معناه أنه لا يُمحى. ونقطةُ
 * {@code DELETE} هنا كانت ستكون البابَ الذي بُني الجدارُ كلُّه لسدّه.</p>
 *
 * <p>ولا شيء مترجَم يُخزَّن: الصفُّ يحمل ثابتَ {@code AuditAction} و{@code details}
 * بصيغة {@code key=value} محايدة، والجملةُ تُبنى عند العرض. فالجوابُ يحمل الاثنين -
 * الثابتَ لمن يصفّي، والمترجَمَ لمن يقرأ.</p>
 *
 * <p>و{@code truncated} ليس تفصيلاً: السقفُ خمسُ مئةٍ في الصفحة الواحدة، وسجلٌّ يعرض
 * ألفاً من عشرة آلافٍ بصمتٍ يدعو قارئه إلى استنتاج أن الباقي لم يقع.</p>
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * @param actor  اسمُ من فعل، نصّاً لا مفتاحاً أجنبياً: حذفُ الحساب أولُ ما يفعله
     *               من يغطّي أثره، ومفتاحٌ أجنبي كان سيمحو أثرَه معه
     * @param entityLabel اسمُ الهدف كما كان لحظتَها - "حُذف الطالب رقم 412" لا يقول شيئاً
     */
    public record EventView(Long id, LocalDateTime at, String actor, String actorRole,
                            String action, String actionName, String category, boolean successful,
                            Long entityId, String entityLabel, String amount, String details) {
    }

    /**
     * @param truncated هل بقي خارج الصفحة ما يطابق التصفية؟
     */
    public record AuditPageView(List<EventView> rows, long totalMatching, boolean truncated) {
    }

    public record OptionView(String name, String label) {
    }

    @GetMapping
    public AuditPageView search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditCategory category) {

        AuditPage page = auditService.search(from, to, actor, category);
        return new AuditPageView(page.rows().stream().map(AuditController::view).toList(),
                page.totalMatching(), page.isTruncated());
    }

    /** أسماءُ من له أثر، لقائمة التصفية */
    @GetMapping("/actors")
    public List<String> actors() {
        return auditService.getActors();
    }

    @GetMapping("/categories")
    public List<OptionView> categories() {
        return Arrays.stream(AuditCategory.values())
                .map(category -> new OptionView(category.name(), category.getDisplayName()))
                .toList();
    }

    /**
     * {@code actorUsername} فارغاً يعني <b>النظام</b>: النسخة المجدولة تجري على خيطٍ بلا
     * جلسة، ونسبتُها إلى آخر من سجّل دخوله كذبة. تصل {@code null} كما هي، والشاشة تقولها.
     *
     * <p>والمبلغ منسَّقاً بعملة السنتر: حركةٌ مالية في السجل بلا مبلغٍ مقروء تُلزم القارئ
     * بفتح جدول الحركات ليعرف عمّ يتكلم السطر.</p>
     */
    private static EventView view(AuditLog row) {
        return new EventView(row.getId(), row.getOccurredAt(), row.getActorUsername(),
                row.getActorRole() == null ? null : row.getActorRole().getDisplayName(),
                row.getAction() == null ? null : row.getAction().name(),
                row.getAction() == null ? null : row.getAction().getDisplayName(),
                row.getAction() == null || row.getAction().getCategory() == null ? null
                        : row.getAction().getCategory().name(),
                row.isSuccessful(),
                row.getEntityId(), row.getEntityLabel(),
                row.getAmount() == null ? null : MoneyUtils.formatWithCurrency(row.getAmount()),
                row.getDetails());
    }
}
