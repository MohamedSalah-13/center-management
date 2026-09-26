package com.codejava.center.web;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
import com.codejava.center.repository.StudentGroupRepository;
import com.codejava.center.repository.StudentRepository;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * تسجيلُ الطالب واشتراكُه من الويب.
 *
 * <p>ما يُفحص هنا هو ما لا يراه اختبار الخدمة: أن المسودة هي المدخل فعلاً، وأن ما
 * ليس فيها لا يصل من جسم الطلب مهما كُتب فيه - وأن الحارس يعبر الحافة بحدوده كما
 * هي: السكرتير يسجّل طالباً ولا يضع جدول السنتر.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class StudentEdgeTest {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long teacherId;
    private Long groupId;

    @BeforeEach
    void seedACentre() {
        // الترتيب هو ترتيب المفاتيح الأجنبية: الأبناء قبل الآباء
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
        teacherId = teacherRepository.save(teacher).getId();

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

    @Test
    void aStudentIsRegisteredThenEditedFromTheWeb() throws Exception {
        MockHttpSession session = signIn("admin");

        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"طالب أول","phone":"01000000001","schoolLevel":"SEC3"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                // باركودٌ لم يُكتب يُولَّد، فلا يُسجَّل طالب بلا بطاقة
                .andExpect(jsonPath("$.barcode").isNotEmpty())
                .andReturn());

        mockMvc.perform(put("/api/students/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"طالب أول","phone":"01000000009","schoolLevel":"SEC3"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("01000000009"));
    }

    /**
     * <b>لا طلبٌ يعيد مؤرشفاً إلى بوابة الحضور.</b>
     *
     * <p>الحقل غائب عن جسم الطلب وعن المسودة معاً، فـ {@code "active":true} فيه لا
     * يجد ما يملؤه. وهذا هو الفرق بين قاعدةٍ تعيش في شاشة وقاعدةٍ تعيش في نوع: شاشةُ
     * سطح المكتب كانت تحرسها بسطرٍ فيها، والحافةُ ما كان لها ذلك السطر.</p>
     */
    @Test
    void noRequestBodyCanRestoreAnArchivedStudent() throws Exception {
        MockHttpSession session = signIn("admin");
        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب منقطع\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/students/" + id + "/archive").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/api/students/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"طالب منقطع","active":true,"isActive":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/students/" + id + "/restore").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * المعرّف من المسار وحده.
     *
     * <p>الرابط التلقائي يتجاهل ما لا يجد له حقلاً، فـ {@code id} في الجسم لا يصل إلى
     * شيء. ولو وصل لكان طلبٌ إلى {@code /api/students/7} يكتب في الطالب رقم 9، والمسار
     * - وهو ما رآه سجلُّ الخادم وما يقرؤه من يراجع - يقول غير ذلك.</p>
     */
    @Test
    void anIdInTheBodyIsIgnoredAndThePathDecides() throws Exception {
        MockHttpSession session = signIn("admin");
        Student other = studentRepository.saveAndFlush(
                Student.builder().barcode("STU-OTHER").name("طالب آخر").build());
        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"صاحب المسار\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(put("/api/students/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + other.getId() + ",\"name\":\"اسمٌ جديد\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("اسمٌ جديد"));

        assertThat(studentRepository.findById(other.getId()))
                .get()
                .extracting(Student::getName)
                .isEqualTo("طالب آخر");
    }

    /** اشتراكٌ ثم إنهاء: الصفُّ يبقى بتاريخ خروج، ولا يُحذف */
    @Test
    void enrollingAndEndingAnEnrolmentFromTheWeb() throws Exception {
        MockHttpSession session = signIn("admin");
        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب مشترك\",\"schoolLevel\":\"SEC3\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/students/" + id + "/enrollments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true));

        mockMvc.perform(delete("/api/students/" + id + "/enrollments/" + groupId)
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                // منتهٍ لا محذوف: المدة هي ما تُحسب عليه حصص الطالب
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].leaveDate").isNotEmpty());
    }

    /**
     * قيدُ الصف يعبر الحافة: {@code 409} لا {@code 400}.
     *
     * <p>حالٌ في القاعدة - صفُّ الطالب وصفُّ المجموعة - لا خطأٌ فيما كُتب: تصحيحُ
     * الطلب لا يغيّر شيئاً، والمطلوب تغييرُ أحد الصفّين.</p>
     */
    @Test
    void enrollingOutsideTheGroupsLevelIsRefused() throws Exception {
        MockHttpSession session = signIn("admin");
        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب إعدادي\",\"schoolLevel\":\"PREP1\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/students/" + id + "/enrollments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isConflict());
    }

    /** المجموعاتُ صارت تُكتب من الويب، والمعلم يصل رقماً لا كياناً */
    @Test
    void aGroupIsCreatedFromTheWeb() throws Exception {
        mockMvc.perform(post("/api/groups").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId":%d,"schoolLevel":"PREP1","maxCapacity":15,
                                 "sessionPrice":"40.00","meetingDays":["SUNDAY"],
                                 "startTime":"10:00","endTime":"12:00","autoName":true}"""
                                .formatted(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("أستاذ رياضيات"))
                .andExpect(jsonPath("$.maxCapacity").value(15))
                // الاسم مشتقٌّ لا مُرسَل: ما يصل في name مهمَلٌ ما دام autoName قائماً
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    /** حدودُ الأدوار تعبر الحافة كما هي: الاستقبال يسجّل الطلاب */
    @Test
    void aSecretaryMayRegisterAStudent() throws Exception {
        mockMvc.perform(post("/api/students").session(signIn("secretary")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب الاستقبال\"}"))
                .andExpect(status().isOk());
    }

    /** ولا يضع جدول السنتر ولا أسعار حصصه - وهو ما تقوله الشاشة بإخفاء الزرّ */
    @Test
    void aSecretaryMayNotCreateAGroup() throws Exception {
        mockMvc.perform(post("/api/groups").session(signIn("secretary")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId":%d,"schoolLevel":"PREP1","maxCapacity":15,
                                 "sessionPrice":"40.00","meetingDays":["SUNDAY"],
                                 "startTime":"10:00","endTime":"12:00","autoName":true}"""
                                .formatted(teacherId)))
                .andExpect(status().isForbidden());
    }

    /** اسمٌ فارغ يُردّ عند الحافة، قبل أن يصل إلى الخدمة */
    @Test
    void aStudentWithoutANameIsRefusedAsBadInput() throws Exception {
        mockMvc.perform(post("/api/students").session(signIn("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    /** الصفوف تصل بثابتها ومترجَمها: الأول يُرسَل والثاني يُعرض */
    @Test
    void theLevelsCarryBothTheirConstantAndTheirName() throws Exception {
        mockMvc.perform(get("/api/students/levels").session(signIn("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SchoolLevel.values().length))
                .andExpect(jsonPath("$[0].name").value(SchoolLevel.values()[0].name()))
                .andExpect(jsonPath("$[0].label")
                        .value(SchoolLevel.values()[0].getDisplayName()));
    }

    /** ولا تسجيل بلا جلسة: الحارس على الخدمة، والحافة لا تلطّفه */
    @Test
    void registeringAStudentNeedsASession() throws Exception {
        mockMvc.perform(post("/api/students").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** ورفضٌ بلا رمز CSRF: الكوكي يركب كل طلب إلى نطاقنا أياً كان من أطلقه */
    @Test
    void aWriteWithoutACsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/students").session(signIn("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب\"}"))
                .andExpect(status().isForbidden());
    }

    /** مجموعةٌ لا وجود لها في الاشتراك: مدخلٌ خاطئ يصلحه من كتبه */
    @Test
    void enrollingIntoAnUnknownGroupIsRefusedAsBadInput() throws Exception {
        MockHttpSession session = signIn("admin");
        long id = idOf(mockMvc.perform(post("/api/students").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"طالب\",\"schoolLevel\":\"SEC3\"}"))
                .andReturn());

        mockMvc.perform(post("/api/students/" + id + "/enrollments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(I18n.get("error.group.notFound")));
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
