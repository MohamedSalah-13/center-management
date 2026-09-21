package com.codejava.center.web;

import com.codejava.center.domain.AlertRule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Transaction;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.AlertAudience;
import com.codejava.center.domain.enums.AlertSeverity;
import com.codejava.center.domain.enums.AlertType;
import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.AlertRepository;
import com.codejava.center.repository.AlertRuleRepository;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.NotificationLogRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.repository.TransactionRepository;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.core.alert.AlertSchedule;
import com.codejava.center.util.I18n;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * التنبيهاتُ ورسائلُ أولياء الأمور من الويب.
 *
 * <p>وما يُختبر هنا ليس أن الحفظ يعمل. ثلاثةُ أبوابٍ تُفتح في هذه الدفعة يُخرج كلٌّ
 * منها رسالةً إلى رقمٍ حقيقي باسم السنتر، فالمُختبَر هو ما <b>لا</b> يصل من جسم
 * الطلب: وجهةٌ إلى أولياء الأمور على نوعٍ لا يقبلها، ونوعٌ في الجسم بدل المسار،
 * ورقمٌ ونصٌّ يختارهما المُرسِل، وسطرُ سجلٍّ عن محادثةٍ لم تُفتح.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AlertEdgeTest {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CenterSettingsRepository settingsRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** الترتيبُ ترتيبُ المفاتيح الأجنبية: الأبناء قبل الآباء، كما في بقية أصناف الحافة */
    @BeforeEach
    void seedACentre() {
        notificationLogRepository.deleteAll();
        alertRepository.deleteAll();
        alertRuleRepository.deleteAll();
        transactionRepository.deleteAll();
        attendanceRepository.deleteAll();
        studentGroupRepository.deleteAll();
        sessionRepository.deleteAll();
        courseGroupRepository.deleteAll();
        teacherRepository.deleteAll();
        studentRepository.deleteAll();
        settingsRepository.deleteAll();
        userRepository.deleteAll();

        save("admin", Role.ADMIN);
        save("secretary", Role.SECRETARY);
    }

    /* ------------------------------------------------------------- القواعد */

    /**
     * <b>لا نوعَ في جسم الطلب.</b> النوعُ مفتاحُ الصفّ، والمسارُ هو ما سجّله الخادم
     * وما يقرؤه المراجع بعد أسبوع. جسمٌ يحمل نوعاً ثانياً يجعل الطلب يقول شيئين عن
     * أيِّ قاعدةٍ يُعدّل.
     */
    @Test
    void aTypeInTheBodyIsIgnoredAndThePathDecides() throws Exception {
        mockMvc.perform(put("/api/alert-rules/ABSENCE").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ARREARS","enabled":false,"audience":"INTERNAL",
                                 "severity":"INFO","threshold":9,"windowDays":60,"cooldownDays":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ABSENCE"))
                .andExpect(jsonPath("$.threshold").value(9));

        // وقاعدةُ ARREARS لم تُمسّ: ما زالت على قيمها الافتراضية بلا صفٍّ محفوظ
        Map<String, Object> arrears = rule("ARREARS");
        assertThat(arrears.get("updatedAt")).isNull();
        assertThat(arrears.get("enabled")).isEqualTo(true);
        assertThat(alertRuleRepository.count()).isEqualTo(1);
    }

    /**
     * <b>نوعٌ لا يبلغ وليَّ أمرٍ تعود وجهتُه داخليةً مهما طلب الجسم.</b>
     *
     * <p>فشلُ نسخةٍ احتياطية لا يُرسَل إلى هاتف ولي أمر، وقبولُ الطلب ثم عدمُ الإرسال
     * يترك في الشاشة وعداً لا يتحقق - وهو أسوأ من رفضٍ صريح.</p>
     */
    @Test
    void anAudienceOfParentsCannotBeSetOnATypeThatCannotReachThem() throws Exception {
        mockMvc.perform(put("/api/alert-rules/BACKUP_FAILED").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"audience":"BOTH","severity":"CRITICAL"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("INTERNAL"))
                .andExpect(jsonPath("$.parentCapable").value(false));
    }

    /**
     * الأرقامُ تصل محلولةً لا فارغة.
     *
     * <p>{@code null} في العمود يعني "استعمل افتراضي النوع"، وحقلٌ فارغ أمام من يضبط
     * قاعدةً لا يقول ما الحدّ العامل الآن. وما لا يستعمله النوع يصل {@code null}
     * ليختفي حقلُه - رقمٌ بجوار عنوانٍ فارغ لا يقول ما هو.</p>
     */
    @Test
    void aStoredRuleWithAnEmptyColumnArrivesWithTheTypesOwnNumber() throws Exception {
        // صفٌّ محفوظ قبل أن يكتسب النوعُ معامله: العمودُ فارغ، ومعناه "استعمل الافتراضي"
        alertRuleRepository.saveAndFlush(AlertRule.builder()
                .type(AlertType.ABSENCE)
                .enabled(true)
                .audience(AlertAudience.INTERNAL)
                .severity(AlertSeverity.WARNING)
                .threshold(null)
                .windowDays(null)
                .cooldownDays(null)
                .build());

        // والشاشةُ لا تفهم الفراغ: حقلٌ خالٍ أمام من يضبط قاعدةً لا يقول ما الحدُّ
        // العامل الآن، ثم يُعيده الحفظ فارغاً فلا يُقرأ ما استُبدل
        Map<String, Object> stored = rule("ABSENCE");
        assertThat(stored.get("threshold")).isEqualTo(AlertType.ABSENCE.getDefaultThreshold());
        assertThat(stored.get("windowDays")).isEqualTo(AlertType.ABSENCE.getDefaultWindowDays());
        assertThat(stored.get("cooldownDays")).isEqualTo(AlertType.ABSENCE.getDefaultCooldownDays());
    }

    @Test
    void anUnsavedRuleArrivesWithTheTypesOwnNumbersResolved() throws Exception {
        Map<String, Object> absence = rule("ABSENCE");
        assertThat(absence.get("threshold")).isEqualTo(AlertType.ABSENCE.getDefaultThreshold());
        assertThat(absence.get("windowDays")).isEqualTo(AlertType.ABSENCE.getDefaultWindowDays());
        assertThat(absence.get("cooldownDays")).isEqualTo(AlertType.ABSENCE.getDefaultCooldownDays());
        assertThat(absence.get("thresholdLabel")).isEqualTo(AlertType.ABSENCE.getThresholdLabel());

        // ARREARS لا يستعمل نافذة: العنوانُ والحقلُ يغيبان معاً
        Map<String, Object> arrears = rule("ARREARS");
        assertThat(arrears.get("windowDays")).isNull();
        assertThat(arrears.get("windowLabel")).isNull();

        // وBACKUP_FAILED حَدَثيّ: يُطلق لحظة وقوعه فلا معنى لتهدئته
        Map<String, Object> backup = rule("BACKUP_FAILED");
        assertThat(backup.get("scheduled")).isEqualTo(false);
        assertThat(backup.get("cooldownDays")).isNull();
        assertThat(backup.get("threshold")).isNull();
    }

    /** ضبطُ التنبيهات سياسةُ السنتر: السكرتير يقرأ الشاشات ولا يقرّر من يُراسَل */
    @Test
    void aSecretaryCannotTouchTheRules() throws Exception {
        mockMvc.perform(get("/api/alert-rules").session(signIn("secretary")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/alert-rules/ABSENCE").session(signIn("secretary")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"audience":"BOTH","severity":"WARNING"}"""))
                .andExpect(status().isForbidden());
    }

    /* -------------------------------------------------------- موعد الفحص */

    /**
     * <b>بابُ الفحص يكتب حقلين لا الصفّ كلَّه</b> - وهو النصفُ الآخر من العطب الذي
     * أصلحته {@code CenterSettingsDraft}.
     *
     * <p>صفُّ الإعدادات واحدٌ وشاشتان تكتبان فيه. حفظُ الإعدادات كان يُطفئ التنبيهات،
     * وحفظُ موعدِ الفحص من الجهة الأخرى يمحو اسمَ السنتر ومسارَ النسخ وختمَها إن كتب
     * الصفَّ كاملاً - ولا شيء يفشل، ولا سطرَ في سجل.</p>
     */
    @Test
    void savingTheScanTimeDoesNotEraseTheRestOfTheSettingsRow() throws Exception {
        LocalDateTime lastBackup = LocalDateTime.of(2026, 9, 21, 2, 0);
        settingsRepository.saveAndFlush(CenterSettings.builder()
                .centerName("سنتر النور")
                .backupPath("D:/backups")
                .autoBackupEnabled(true)
                .lastAutoBackupAt(lastBackup)
                .build());

        mockMvc.perform(put("/api/alerts/scan-settings").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"time\":\"18:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.time").value(startsWith("18:30")));

        mockMvc.perform(get("/api/settings").session(signIn("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerName").value("سنتر النور"))
                .andExpect(jsonPath("$.backupPath").value("D:/backups"))
                .andExpect(jsonPath("$.autoBackupEnabled").value(true))
                .andExpect(jsonPath("$.lastAutoBackupAt").value(startsWith("2026-09-21T02:00")));
    }

    /** موعدٌ غائب يعني الافتراضي لا منتصفَ الليل: حقلٌ فارغ ليس اختياراً للساعة صفر */
    @Test
    void anAbsentScanTimeFallsBackToTheDefaultSlot() throws Exception {
        mockMvc.perform(put("/api/alerts/scan-settings").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"time\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time").value(startsWith(
                        AlertSchedule.DEFAULT_TIME.toString())));
    }

    /* ------------------------------------------------------------- الصندوق */

    /**
     * <b>المعالجةُ لا تحذف ولا تُنقض.</b>
     *
     * <p>"متى انتهت هذه المشكلة ومن نظر فيها" سؤالٌ يُطرح بعد أسبوع، والحذف يجعل
     * جوابه مستحيلاً. وضغطةٌ ثانية لا تُعيد التنبيه قائماً: الصفُّ يبقى، والعدّادُ
     * يقول صفراً لأن شيئاً لم يتغيّر.</p>
     */
    @Test
    void acknowledgingNeitherDeletesTheRowNorCanBeUndone() throws Exception {
        MockHttpSession session = signIn("admin");
        long id = raiseAnAlert();

        mockMvc.perform(post("/api/alerts/acknowledge").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(1))
                .andExpect(jsonPath("$.unacknowledged").value(0));

        mockMvc.perform(post("/api/alerts/acknowledge").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(0));

        assertThat(alertRepository.findById(id)).isPresent();
        mockMvc.perform(get("/api/alerts?from=" + LocalDate.now().minusDays(1)
                        + "&to=" + LocalDate.now()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(id))
                .andExpect(jsonPath("$.rows[0].acknowledged").value(true))
                .andExpect(jsonPath("$.rows[0].acknowledgedBy").value("admin"));
    }

    /* ------------------------------------------- رسائل أولياء الأمور */

    /**
     * <b>المرشَّحُ يُعاد بناؤه على الخادم، ومن ليس فيه يُردّ.</b>
     *
     * <p>ولو قُبل من جسم الطلب لكان كلُّ من يملك جلسةً يُرسل أيَّ نصٍّ إلى أيِّ رقم
     * من حساب السنتر عند المزوّد - على فاتورته وباسمه. الطلبُ يصف القائمة ورقمَ
     * الطالب، والخادمُ يأخذ منها صاحبَه.</p>
     */
    @Test
    void aStudentOutsideTheRebuiltListCannotBeMessaged() throws Exception {
        MockHttpSession session = signIn("admin");
        Student owing = studentWithADebt("سارة", "01001234567");
        Student settled = saveStudent("خالد", "01007654321");

        mockMvc.perform(get("/api/notifications/candidates?type=ARREARS").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(owing.getId()));

        // وخالدٌ ليس مديناً: رقمُه صحيحٌ وموجود، والقائمةُ وحدها هي من تقرّر
        mockMvc.perform(post("/api/notifications/send").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ARREARS\",\"studentId\":" + settled.getId() + "}"))
                // 409 لا 400: الحالةُ في قاعدة البيانات هي ما تغيّر، ولا جسمَ طلبٍ يصلحها
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(I18n.get("error.notification.candidateGone")));

        assertThat(notificationLogRepository.count()).isZero();
    }

    /**
     * ولا حقلَ للرقم ولا للنصّ أصلاً: ما لا يُصرَّح به لا يصل إلى شيء.
     *
     * <p>تجاهلُ الحقل لا يكفي - الحقلُ غيرُ معلَن، فـ{@code "phone"} في الجسم يرتبط
     * بلا شيء. والرسالةُ تُبنى على الخادم من قالبٍ باسم السنتر.</p>
     */
    @Test
    void neitherThePhoneNorTheMessageCanBeChosenByTheSender() throws Exception {
        MockHttpSession session = signIn("admin");
        Student owing = studentWithADebt("سارة", "01001234567");

        mockMvc.perform(post("/api/notifications/send").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ARREARS\",\"studentId\":" + owing.getId() + ","
                                + "\"phone\":\"01999999999\",\"message\":\"حوّل ألفاً على هذا الرقم\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsHandOff").value(true));

        // ولا سطرَ في السجل: التسليمُ إلى إنسانٍ ليس إرسالاً
        assertThat(notificationLogRepository.count()).isZero();
    }

    /**
     * <b>التسليمُ إلى إنسانٍ ليس نجاحاً، والسطرُ يكتبه من فتح المحادثة.</b>
     *
     * <p>سطرُ {@code notification_logs} يعني "فُتحت محادثةُ وليّ الأمر بالرسالة فيها".
     * كتابتُه لحظةَ بناء الرابط تسجّل إشعاراً لمحادثةٍ لم تُفتح، ثم يمنع حارسُ التكرار
     * إعادةَ المحاولة - فيبقى وليُّ أمرٍ بلا خبر ولا أحد يدري.</p>
     */
    @Test
    void aHandOffWritesNothingUntilSomebodySaysTheChatWasOpened() throws Exception {
        MockHttpSession session = signIn("admin");
        Student owing = studentWithADebt("سارة", "01001234567");
        String body = "{\"type\":\"ARREARS\",\"studentId\":" + owing.getId() + "}";

        mockMvc.perform(post("/api/notifications/send").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.needsHandOff").value(true))
                // والرابطُ رابطُ متصفّح: {@code whatsapp://} يفتحه معالجُ بروتوكولٍ على جهاز،
                // ولا معالجَ له في تبويبٍ مفتوح على خادم
                .andExpect(jsonPath("$.link").value(startsWith("https://wa.me/")));

        assertThat(notificationLogRepository.count()).isZero();

        mockMvc.perform(post("/api/notifications/opened").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(notificationLogRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/notifications/log").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(owing.getId()))
                .andExpect(jsonPath("$[0].type").value("ARREARS"));
    }

    /**
     * كلُّ نوعٍ يعرضه {@code /types} له قائمةٌ تُبنى فعلاً.
     *
     * <p>القائمتان - ما يُعرض وما يُبنى - تفترقان يوماً ما إن كُتبتا مرتين: نوعٌ
     * يُعرض ولا يُبنى يُردّ بعد أن يختاره الموظف، ونوعٌ يُبنى ولا يُعرض يبقى غيرَ
     * مستعمَلٍ بلا أن يقول أحدٌ شيئاً.</p>
     */
    @Test
    void everySelectableTypeBuildsAList() throws Exception {
        MockHttpSession session = signIn("admin");
        List<Map<String, Object>> types = read(
                mockMvc.perform(get("/api/notifications/types").session(session))
                        .andExpect(status().isOk())
                        .andReturn());

        assertThat(types).extracting(type -> type.get("name"))
                .containsExactlyInAnyOrder("ABSENCE", "ARREARS");

        // ARREARS يُبنى بلا شيء؛ ABSENCE يحتاج مجموعةً ومدة، و/types يقول ذلك بنفسه
        assertThat(types).allSatisfy(type -> {
            String name = (String) type.get("name");
            assertThat(type.get("needsGroup")).isEqualTo("ABSENCE".equals(name));
            assertThat((String) type.get("label")).isNotBlank();
        });

        // وما لا يحتاج مجموعةً يُبنى الآن، فيُقطع الطريقُ على قائمةٍ تُعرض ولا تُبنى
        for (Map<String, Object> type : types) {
            if (Boolean.TRUE.equals(type.get("needsGroup"))) {
                continue;
            }
            mockMvc.perform(get("/api/notifications/candidates?type=" + type.get("name"))
                            .session(session))
                    .andExpect(status().isOk());
        }
    }

    /** ونوعٌ لا قائمةَ له يُردّ بجملته لا بـ500 يقرؤه المرسِل "ليست مشكلتي" */
    @Test
    void aTypeWithNoListIsRefusedInWords() throws Exception {
        mockMvc.perform(get("/api/notifications/candidates?type=BACKUP_FAILED").session(signIn("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(I18n.format(
                        "error.notification.typeNotSelectable",
                        AlertType.BACKUP_FAILED.getDisplayName())));
    }

    /* ------------------------------------------------------------ التركيب */

    /**
     * قناةُ الرابط: لا مفتاحَ يلزمها ولا مزوّد، فهي ما يجعل مسارَ التسليم إلى إنسان
     * قابلاً للاختبار هنا دون أن يخرج طلبٌ إلى الشبكة.
     */
    private Student studentWithADebt(String name, String parentPhone) {
        settingsRepository.saveAndFlush(CenterSettings.builder()
                .centerName("سنتر النور")
                .notificationChannel(NotificationChannel.WHATSAPP_LINK)
                .build());

        Student student = saveStudent(name, parentPhone);
        transactionRepository.saveAndFlush(Transaction.builder()
                .type(TransactionType.SESSION_CHARGE)
                .amount(new BigDecimal("300.00"))
                .description("حصة")
                .transactionDate(LocalDateTime.now())
                .student(student)
                .build());

        return student;
    }

    private Student saveStudent(String name, String parentPhone) {
        Student student = new Student();
        student.setName(name);
        student.setBarcode(UUID.randomUUID().toString().substring(0, 8));
        student.setParentPhone(parentPhone);
        student.setActive(true);
        return studentRepository.saveAndFlush(student);
    }

    /** تنبيهٌ في الصندوق: يُكتب مباشرةً، فالمقصود هنا بابُ المعالجة لا المحرِّك */
    private long raiseAnAlert() {
        com.codejava.center.domain.Alert alert = com.codejava.center.domain.Alert.builder()
                .type(AlertType.BACKUP_FAILED)
                .severity(com.codejava.center.domain.enums.AlertSeverity.CRITICAL)
                .raisedAt(LocalDateTime.now())
                .dedupeKey(UUID.randomUUID().toString())
                .build();

        return alertRepository.saveAndFlush(alert).getId();
    }

    /** صفُّ قاعدةٍ بنوعه: الترتيبُ في الجواب ترتيبُ الثوابت، ونوعٌ يُضاف غداً يزحزحه */
    private Map<String, Object> rule(String type) throws Exception {
        return read(mockMvc.perform(get("/api/alert-rules").session(signIn("admin")))
                .andExpect(status().isOk())
                .andReturn()).stream()
                .filter(row -> type.equals(row.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("لا قاعدة للنوع " + type));
    }

    private List<Map<String, Object>> read(MvcResult result) throws Exception {
        return new ObjectMapper().readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
    }

    private MockHttpSession signIn(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centre\":\"\",\"username\":\"" + username
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private void save(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        userRepository.save(user);
    }
}
