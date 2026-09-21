package com.codejava.center.platform;

import com.codejava.center.core.tenant.SchemaName;
import com.codejava.center.core.tenant.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * من هي المؤسسات، مقروءةً من سجلّ المنصة ومحفوظةً في الذاكرة.
 *
 * <p><b>الذاكرة المؤقتة ليست تحسيناً بل شرط عمل.</b> موجِّهُ الاتصال يسأل "أيّ قاعدة
 * لهذه المؤسسة؟" عند كل جلسة Hibernate - أي عند كل معاملة، أي عشرات المرات في الطلب
 * الواحد. استعلامٌ في كل مرة يضع قاعدة المنصة على مسار كل استعلام في المنصة كلها.</p>
 *
 * <p>وهي لقطةٌ كاملة تُستبدل دفعةً واحدة ({@code volatile}) لا خريطةٌ تُعدَّل: قارئٌ
 * يمرّ أثناء التحديث يرى الصورة القديمة كاملةً أو الجديدة كاملةً، ولا يرى مؤسسةً
 * نصفها قديم. والتحديث يقع عند الإقلاع وبعد كل تزويد أو تغيير حال - لا بمؤقّت: خادمٌ
 * واحد يكتب وهو نفسه يُحدِّث، وما يكتبه خادمٌ آخر يصل بإعادة القراءة الصريحة.</p>
 */
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);

    private static final String SELECT_ALL = """
            SELECT id, name, slug, schema_name, status, paid_through
            FROM tenants
            ORDER BY id
            """;

    private final JdbcTemplate platform;

    /**
     * ساعةُ البرنامج، لأن "من تُخدَم" صار سؤالاً عن اليوم.
     *
     * <p>{@link java.time.LocalDate#now()} يقرأ ساعةَ الجهاز ومنطقتَه من داخل الدالة،
     * فلا يمكن وضعُ يوم الانقضاء على حدٍّ في اختبار - وحدُّ الانقضاء هو بالضبط ما
     * يُغلق أبوابَ سنترٍ دافع حين يخطئ بيوم.</p>
     */
    private final Clock clock;

    private volatile Map<TenantId, PlatformTenant> byId = Map.of();

    public TenantRegistry(JdbcTemplate platform, Clock clock) {
        this.platform = platform;
        this.clock = clock;
    }

    /** يعيد قراءة السجلّ كاملاً. يُستدعى عند الإقلاع وبعد كل تغيير في المنصة. */
    public void refresh() {
        List<PlatformTenant> rows = platform.query(SELECT_ALL, (resultSet, index) ->
                new PlatformTenant(
                        new TenantId(resultSet.getLong("id")),
                        resultSet.getString("name"),
                        resultSet.getString("slug"),
                        SchemaName.of(resultSet.getString("schema_name")),
                        TenantStatus.valueOf(resultSet.getString("status")),
                        // getDate يعيد null للعمود الفارغ، وهو المعنى المقصود: لا اشتراك يُتابَع
                        resultSet.getDate("paid_through") == null
                                ? null : resultSet.getDate("paid_through").toLocalDate()));

        // ترتيبُ المعرّف محفوظ: الاستعلام يرتّب، وخريطةٌ لا تحفظ الترتيب تُضيّعه. وهو
        // يهمّ لأن الدورة الليلية تمرّ بهذا الترتيب: سجلٌّ يقول "توقفت عند الثالثة"
        // كل ليلة عند مؤسسة مختلفة لا يُقرأ منه شيء
        Map<TenantId, PlatformTenant> ordered = new LinkedHashMap<>();
        rows.forEach(tenant -> ordered.put(tenant.id(), tenant));
        this.byId = Collections.unmodifiableMap(ordered);
        LocalDate today = LocalDate.now(clock);
        log.info("سجلّ المنصة: {} مؤسسة، منها {} تُخدَم", rows.size(),
                rows.stream().filter(tenant -> tenant.isServedOn(today)).count());
    }

    public Optional<PlatformTenant> find(TenantId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<PlatformTenant> findBySlug(String slug) {
        return all().stream().filter(tenant -> tenant.slug().equals(slug)).findFirst();
    }

    public List<PlatformTenant> all() {
        return List.copyOf(byId.values());
    }

    /**
     * المؤسسات التي تعمل لها المهام التلقائية: يسمح المشغّل، والاشتراك قائم.
     *
     * <p>والانقضاء يُحسب هنا عند كل قراءة لا يكتبه مجدوِل: فلا وجود لليلةٍ لم تعمل
     * فيها دورةُ الإيقاف فبقي سنترٌ منقضٍ يُخدَم إلى أن ينتبه أحد.</p>
     */
    public List<PlatformTenant> served() {
        LocalDate today = LocalDate.now(clock);
        return byId.values().stream().filter(tenant -> tenant.isServedOn(today)).toList();
    }

    /** المؤسسات التي تُطبَّق عليها ترحيلات المخطط */
    public List<PlatformTenant> migrated() {
        return byId.values().stream().filter(tenant -> tenant.status().isMigrated()).toList();
    }

    /**
     * قاعدةُ هذه المؤسسة.
     *
     * <p>يُرمى لا يُسقط إلى قاعدةٍ افتراضية: معرّفٌ لا يعرفه السجلّ يعني إمّا مؤسسةً
     * حُذفت وبقيت جلسةٌ تشير إليها، وإمّا معرّفاً وصل من حيث لا يجب. السقوط إلى
     * الافتراضي في الحالتين يكتب بيانات مؤسسة في قاعدة أخرى.</p>
     */
    public SchemaName schemaOf(TenantId id) {
        return find(id).orElseThrow(() -> new IllegalStateException(
                "no tenant registered with id " + id.value())).schema();
    }
}
