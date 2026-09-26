package com.codejava.center.web;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.domain.enums.TransactionType;
import com.codejava.center.repository.AttendanceRepository;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.repository.TransactionRepository;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * المال على الحافة: المصروفات على مدى فترة، وحركاتُ طالب، ومستحقاتُ معلم.
 *
 * <p>وأثقلُ ما هنا رحلةٌ كاملة تمرّ بأربعة مسارات: يدفع الطالب، فيُمرَّر كارنيهه،
 * فتُغلق الحصة، فتظهر مستحقةً للصرف. لا اختبارَ خدمةٍ واحد يراها، لأن كلَّ حلقةٍ فيها
 * صحيحةٌ وحدها والخطأ يقع في الوصل - وهي أيضاً الرحلة التي صارت ممكنةً على الويب
 * بعد أن صار يفتح الحصص ويغلقها.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MoneyEdgeTest {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long teacherId;
    private Long groupId;

    @BeforeEach
    void seedACentre() {
        // الترتيب هو ترتيب المفاتيح الأجنبية: الأبناء قبل الآباء
        transactionRepository.deleteAll();
        attendanceRepository.deleteAll();
        studentGroupRepository.deleteAll();
        sessionRepository.deleteAll();
        courseGroupRepository.deleteAll();
        teacherRepository.deleteAll();
        studentRepository.deleteAll();
        userRepository.deleteAll();

        save("admin", Role.ADMIN);
        save("secretary", Role.SECRETARY);

        Teacher teacher = new Teacher();
        teacher.setName("أستاذ رياضيات");
        teacher.setSubjectDefinition(subject("رياضيات"));
        teacher.setCommissionType("PERCENTAGE");
        teacher.setCommissionValue(new BigDecimal("50.00"));
        teacher = teacherRepository.save(teacher);
        teacherId = teacher.getId();

        groupId = courseGroupRepository.save(CourseGroup.builder()
                .name("مجموعة الثالث الثانوي")
                .teacher(teacher)
                .schoolLevel(SchoolLevel.SEC3)
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("50.00"))
                .meetingDays(Set.of(DayOfWeek.SATURDAY))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(18, 0))
                .autoName(false)
                .build()).getId();
    }

    /**
     * البحث يضيّق الإجمالي ويُكتب في سطر المدى.
     *
     * <p>هذا هو الخطأ الذي يوجد هذا المسار من أجله: من يبحث عن "كهرباء" ثم يقرأ
     * إجمالياً يشمل كل المصروفات ينسب مصروفات الشهر كلها إلى فاتورة الكهرباء. ولذلك
     * تقع التصفية على الخادم - فالورقة والإجمالي والقائمة تخرج من قراءةٍ واحدة.</p>
     */
    @Test
    void theSearchNarrowsTheTotalAndIsNamedInTheScopeLine() throws Exception {
        MockHttpSession session = signIn("admin");
        recordExpense(session, "300.00", "فاتورة كهرباء");
        recordExpense(session, "120.00", "أدوات نظافة");

        String period = "from=" + LocalDate.now() + "&to=" + LocalDate.now();

        mockMvc.perform(get("/api/expenses?" + period).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.total").value(420.00));

        mockMvc.perform(get("/api/expenses?" + period + "&query=كهرباء").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.total").value(300.00))
                .andExpect(jsonPath("$.largest").value(300.00))
                // ووصفُ المدى يحمل البحث، فلا تُقرأ الورقة كشفَ الفترة كلها
                .andExpect(jsonPath("$.scope").value(
                        org.hamcrest.Matchers.containsString("كهرباء")));
    }

    /** وكشفُ الخزينة للمدير: السكرتير يسجّل المصروف ولا يقرأ مصروفات الشهر */
    @Test
    void aSecretaryMayNotReadTheExpenseReport() throws Exception {
        mockMvc.perform(get("/api/expenses?from=" + LocalDate.now() + "&to=" + LocalDate.now())
                        .session(signIn("secretary")))
                .andExpect(status().isForbidden());
    }

    /**
     * الرحلة كاملة: دفعٌ، فتمريرةٌ، فإغلاقٌ، فصرف.
     *
     * <p>وفي آخرها تخرج الحصة من قائمة المستحقات ويظهر الصرف في حركة اليوم: الصرفُ
     * مالٌ يخرج من الدرج، فلو لم يُكتب في الخزينة لَتطابق الدرج مع الورق ونقص المال.</p>
     */
    @Test
    void aPaidScannedAndClosedSessionBecomesPayableThenLeavesTheList() throws Exception {
        MockHttpSession session = signIn("admin");
        long studentId = enrolledStudent(session);

        // رصيدٌ يكفي رسم الحصة: البوابة ترد من لا رصيد له
        mockMvc.perform(post("/api/till/payments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"STU-MONEY","groupId":%d,"amount":"200.00",
                                 "description":"اشتراك الشهر"}"""
                                .formatted(groupId)))
                .andExpect(status().isOk());

        long sessionId = idOf(mockMvc.perform(post("/api/class-sessions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/attendance/scan").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"STU-MONEY\",\"sessionId\":" + sessionId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // حصةٌ ما زالت مفتوحة لا تُصرَف: قد يُمرَّر كارنيهٌ بعدُ فينقص إيرادها
        mockMvc.perform(post("/api/teacher-payouts/" + sessionId).session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(I18n.get("error.teacher.sessionStillOpen")));

        mockMvc.perform(post("/api/class-sessions/" + sessionId + "/close")
                        .session(session).with(csrf()))
                .andExpect(status().isOk());

        // حاضرٌ واحد بسعر 50 ونسبة 50% = 25
        mockMvc.perform(get("/api/teacher-payouts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$[0].attendees").value(1))
                .andExpect(jsonPath("$[0].enrolled").value(1))
                .andExpect(jsonPath("$[0].revenue").value(50.00))
                .andExpect(jsonPath("$[0].payout").value(25.00));

        mockMvc.perform(post("/api/teacher-payouts/" + sessionId).session(session).with(csrf()))
                .andExpect(status().isOk())
                // الجواب هو القائمة من جديد، فلا يبقى سطرٌ صُرف توّاً ليُضغط ثانيةً
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/till/summary?date=" + LocalDate.now()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payouts").value(25.00));

        // ولا يُصرف مرتين
        mockMvc.perform(post("/api/teacher-payouts/" + sessionId).session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(I18n.get("error.teacher.payoutAlreadyDone")));
    }

    /**
     * سجلُّ الحركات يفرّق الداخل من الخارج.
     *
     * <p>قائمةٌ لا تحمل النوع تُقرأ مدفوعاتٍ كلَّها، فيصير خصمُ رسم الحصة دفعةً في عين
     * من ينظر - ويصير مجموعُ "ما دفع" ضعفَ حقيقته.</p>
     */
    @Test
    void theMovementsSeparatePaymentsFromSessionFees() throws Exception {
        MockHttpSession session = signIn("admin");
        long studentId = enrolledStudent(session);

        mockMvc.perform(post("/api/till/payments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"STU-MONEY","groupId":%d,"amount":"200.00",
                                 "description":"اشتراك الشهر"}"""
                                .formatted(groupId)))
                .andExpect(status().isOk());

        long sessionId = idOf(mockMvc.perform(post("/api/class-sessions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andReturn());

        mockMvc.perform(post("/api/attendance/scan").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"STU-MONEY\",\"sessionId\":" + sessionId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/students/" + studentId + "/payments").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.paid").value(200.00))
                .andExpect(jsonPath("$.charged").value(50.00))
                .andExpect(jsonPath("$.balance").value(150.00))
                .andExpect(jsonPath("$.rows[*].type", org.hamcrest.Matchers.containsInAnyOrder(
                        TransactionType.INCOME.name(), TransactionType.SESSION_CHARGE.name())));
    }

    /** وحركاتُ طالبٍ واحد قراءةٌ مفتوحة: هي عنه، ولا تقول عن الخزينة شيئاً */
    @Test
    void aSecretaryMayReadOneStudentsMovements() throws Exception {
        long studentId = enrolledStudent(signIn("admin"));

        mockMvc.perform(get("/api/students/" + studentId + "/payments").session(signIn("secretary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    /** أما المستحقات فلا: من يوقّع صرف المال هو صاحب السنتر */
    @Test
    void aSecretaryMayNotListOrPayPayouts() throws Exception {
        MockHttpSession secretary = signIn("secretary");

        mockMvc.perform(get("/api/teacher-payouts").session(secretary))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/teacher-payouts/1").session(secretary).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * حركةٌ بلا بيان تُردّ بجملةٍ مترجَمة، لا بقيدِ عمودٍ في القاعدة.
     *
     * <p>الشاشتان تفحصانه في نفسيهما منذ البداية، والحافة لم تكن تفحصه - فكان
     * {@code NOT NULL} يصل {@code 500} بجملة "حدث خطأ غير متوقع" عن مبلغٍ دخل الدرج
     * فعلاً في ظنّ من ضغط الزرّ.</p>
     */
    @Test
    void aTillMovementWithoutADescriptionIsRefusedInWords() throws Exception {
        MockHttpSession session = signIn("admin");

        mockMvc.perform(post("/api/till/expenses").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"50.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(I18n.get("error.transaction.descriptionRequired")));

        enrolledStudent(session);
        mockMvc.perform(post("/api/till/payments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"STU-MONEY\",\"amount\":\"50.00\",\"description\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(I18n.get("error.transaction.descriptionRequired")));
    }

    /** حصةٌ لا وجود لها: مدخلٌ خاطئ يصلحه من كتبه، لا حالٌ في القاعدة */
    @Test
    void payingOutAnUnknownSessionIsRefusedAsBadInput() throws Exception {
        mockMvc.perform(post("/api/teacher-payouts/999999").session(signIn("admin")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(I18n.get("error.session.notFound")));
    }

    private long enrolledStudent(MockHttpSession session) throws Exception {
        long studentId = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"STU-MONEY","name":"طالب دافع","schoolLevel":"SEC3"}"""))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/students/" + studentId + "/enrollments")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isOk());

        return studentId;
    }

    private void recordExpense(MockHttpSession session, String amount, String description)
            throws Exception {
        mockMvc.perform(post("/api/till/expenses").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"" + amount + "\",\"description\":\""
                                + description + "\"}"))
                .andExpect(status().isOk());
    }

    // Number لا Long: JsonPath يعيد Integer لرقمٍ صغير، وتحويلٌ مباشر يسقط
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
    @org.springframework.beans.factory.annotation.Autowired
    private com.codejava.center.repository.SubjectRepository subjectRepository;
    private com.codejava.center.domain.Subject subject(String name) {
        String key = com.codejava.center.core.catalog.SubjectNames.key(name);
        return subjectRepository.findByNameKey(key).orElseGet(() -> subjectRepository.saveAndFlush(
                new com.codejava.center.domain.Subject(name, key)));
    }
}
