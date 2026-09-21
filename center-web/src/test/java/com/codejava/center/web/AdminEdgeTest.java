package com.codejava.center.web;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.AuditLogRepository;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TransactionRepository;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.repository.UserRepository;
import com.codejava.center.util.I18n;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * إدارةُ السنتر من الويب: الإعدادات والمعلمون والحسابات وسجلّ المراقبة.
 *
 * <p>وأهمُّ ما هنا ليس أن الحفظ يعمل، بل أن <b>ما لا تحمله المسودة لا يصل</b>: صفُّ
 * الإعدادات واحد، وكلُّ كاتبٍ للصفّ كاملاً يمحو ما ضبطه غيره - وهو ما كان يقع على
 * الجهاز نفسه قبل هذه الدفعة.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AdminEdgeTest {

    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String NEW_PASSWORD = UUID.randomUUID() + "aA1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private CenterSettingsRepository settingsRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * أصنافُ {@code @SpringBootTest} في هذه الوحدة تتشارك قاعدةً واحدة، فما يتركه صفٌّ
     * يجده الذي بعده. والترتيب هو ترتيب المفاتيح الأجنبية: الأبناء قبل الآباء.
     */
    @BeforeEach
    void seedACentre() {
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

    /**
     * <b>حفظُ الإعدادات لا يُطفئ التنبيهات ولا يمحو ختمَي المجدوِلين.</b>
     *
     * <p>كان يفعل: الشاشة تبني {@code CenterSettings} كاملاً وهي لا تحمل حقلَ تنبيهاتٍ
     * واحداً. والحافةُ أسوأ، إذ لا سطرَ فيها يحمل الختمَ بيده كما كانت تفعل الشاشة.</p>
     */
    @Test
    void savingSettingsDoesNotSilenceAlertsNorEraseTheSchedulerStamps() throws Exception {
        LocalDateTime lastScan = LocalDateTime.of(2026, 9, 20, 18, 30);
        LocalDateTime lastBackup = LocalDateTime.of(2026, 9, 21, 2, 0);
        settingsRepository.saveAndFlush(CenterSettings.builder()
                .alertsEnabled(true)
                .alertScanTime(LocalTime.of(18, 0))
                .lastAlertScanAt(lastScan)
                .lastAutoBackupAt(lastBackup)
                .build());

        // والجسمُ يحمل الأربعة صراحةً: لا حقلَ لها في المسودة، فلا تصل إلى شيء
        mockMvc.perform(put("/api/settings").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"centerName":"سنتر النور","currency":"EGP","autoBackupEnabled":true,
                                 "alertsEnabled":false,"alertScanTime":"03:00",
                                 "lastAlertScanAt":null,"lastAutoBackupAt":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerName").value("سنتر النور"))
                .andExpect(jsonPath("$.alertsEnabled").value(true))
                .andExpect(jsonPath("$.alertScanTime").value("18:00:00"))
                .andExpect(jsonPath("$.lastAlertScanAt").value(org.hamcrest.Matchers.startsWith("2026-09-20T18:30")))
                .andExpect(jsonPath("$.lastAutoBackupAt").value(org.hamcrest.Matchers.startsWith("2026-09-21T02:00")));
    }

    /** والعملةُ الغائبة تصل افتراضيةً لا فارغة: قاعدةٌ مُرقّاة كل مبالغها بالجنيه فعلاً */
    @Test
    void anAbsentCurrencyReadsAsTheDefault() throws Exception {
        settingsRepository.saveAndFlush(CenterSettings.builder().build());

        mockMvc.perform(get("/api/settings").session(signIn("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyName").value("EGP"));
    }

    /** إعداداتُ السنتر سياستُه: السكرتير لا يمسّ تاريخَ بداية دفتر الحسابات */
    @Test
    void aSecretaryMayNotSaveSettings() throws Exception {
        mockMvc.perform(put("/api/settings").session(signIn("secretary")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerName\":\"سنتر\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * جوابُ الحسابات لا يحمل بصمةً ولا حقلاً يشبهها.
     *
     * <p>البصمة ليست سرّاً كالكلمة، لكنها تُجرَّب خارج البرنامج بلا قفلٍ ولا مهلة -
     * و{@code LoginThrottle} لا يحرس إلا الباب.</p>
     */
    @Test
    void theUsersResponseNeverCarriesAPasswordHash() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users").session(signIn("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void aUserIsCreatedThenDeleted() throws Exception {
        MockHttpSession session = signIn("admin");

        long id = idOf(mockMvc.perform(post("/api/users").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"clerk","role":"SECRETARY",
                                 "password":"%s","confirmation":"%s"}"""
                                .formatted(NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SECRETARY"))
                .andReturn());

        mockMvc.perform(delete("/api/users/" + id).session(session).with(csrf()))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll()).extracting(User::getUsername)
                .doesNotContain("clerk");
    }

    /** تأكيدٌ لا يطابق: خطأُ حرفٍ في كلمةٍ لا تُعرض يُقفل الحساب على صاحبه */
    @Test
    void aMismatchedConfirmationIsRefused() throws Exception {
        mockMvc.perform(post("/api/users").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"clerk","role":"SECRETARY",
                                 "password":"%s","confirmation":"something-else"}"""
                                .formatted(NEW_PASSWORD)))
                .andExpect(status().is4xxClientError());
    }

    /** وحساباتُ المستخدمين كلُّها للمدير: من يصنع الحسابات يملك البرنامج */
    @Test
    void aSecretaryMayNotListUsers() throws Exception {
        mockMvc.perform(get("/api/users").session(signIn("secretary")))
                .andExpect(status().isForbidden());
    }

    /**
     * نوعُ عمولةٍ لا يعرفه الحساب يُردّ عند الحفظ.
     *
     * <p>ولو قُبل لَكان معلماً سليماً في كل شاشة يسقط صرفُ كل حصةٍ له وحده - بعد
     * أسابيع، أمام من يعدّ المال.</p>
     */
    @Test
    void aTeacherWithAnUnknownCommissionTypeIsRefused() throws Exception {
        mockMvc.perform(post("/api/teachers").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"أ/ سامي","subject":"لغات",
                                 "commissionType":"HALF_OF_WHATEVER","commissionValue":"10"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(I18n.format("error.teacher.unknownCommission", "HALF_OF_WHATEVER")));
    }

    /** وما يتقاضاه المعلم لا يُسلَّم في القائمة التي يطلبها نموذجُ المجموعة */
    @Test
    void theTeacherListWithholdsTheCommissionUnlessAsked() throws Exception {
        MockHttpSession session = signIn("admin");
        mockMvc.perform(post("/api/teachers").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"أ/ محمد","subject":"رياضيات",
                                 "commissionType":"PERCENTAGE","commissionValue":"50"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teachers").session(session))
                .andExpect(jsonPath("$[0].name").value("أ/ محمد"))
                .andExpect(jsonPath("$[0].commissionValue").doesNotExist());

        mockMvc.perform(get("/api/teachers?withCommission=true").session(session))
                .andExpect(jsonPath("$[0].commissionValue").value(50.00));
    }

    /**
     * <b>لا بابَ لمحو سجلّ المراقبة.</b>
     *
     * <p>{@code AuditLogRepository} لا يملك {@code delete} أصلاً، ونقطةُ {@code DELETE}
     * هنا كانت ستكون البابَ الذي بُني الجدارُ كلُّه لسدّه. و{@code 405} لا {@code 500}:
     * طريقةٌ لا يقبلها المسار جوابٌ عنها لا خطأٌ في الخادم.</p>
     */
    @Test
    void theAuditTrailHasNoDeleteDoor() throws Exception {
        mockMvc.perform(delete("/api/audit").session(signIn("admin")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    /** وصفوفُه تحمل الثابتَ لمن يصفّي والمترجَمَ لمن يقرأ */
    @Test
    void auditRowsCarryBothTheConstantAndItsTranslation() throws Exception {
        MockHttpSession session = signIn("admin");
        LocalDate day = LocalDate.now();

        assertThat(auditLogRepository.countMatching(day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(), "admin", java.util.List.of(
                        com.codejava.center.domain.enums.AuditAction.LOGIN_SUCCEEDED)))
                .isPositive();

        mockMvc.perform(get("/api/audit?from=" + day + "&to=" + day + "&actor=admin")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].action").exists())
                .andExpect(jsonPath("$.rows[0].actionName").exists())
                .andExpect(jsonPath("$.truncated").value(false));
    }

    /** وجسمٌ لا يُقرأ مدخلٌ خاطئ يصلحه من كتبه، لا "خطأ غير متوقع" */
    @Test
    void anUnreadableBodyIsRefusedAsBadInput() throws Exception {
        mockMvc.perform(put("/api/settings").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
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
