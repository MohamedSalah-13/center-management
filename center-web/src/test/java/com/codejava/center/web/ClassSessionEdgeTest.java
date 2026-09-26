package com.codejava.center.web;

import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.domain.enums.SchoolLevel;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.SessionRepository;
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
 * فتحُ الحصة وإغلاقها من الويب.
 *
 * <p>هذه هي الميزة التي كانت ناقصة: بوابة الحضور تحتاج حصةً مفتوحة، ولم يكن يفتحها
 * إلا جهاز سطح المكتب - فسنترٌ يريد شاشاته على الشبكة كان يحتاج جهازاً ليبدأ يومه.</p>
 *
 * <p>وما يُفحص هنا هو ما لا يراه اختبار الخدمة: أن المسار موصول، وأن الحارس يعبر
 * الحافة كما هو، وأن حالة الحصة تصل كما هي بعد كل عملية.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ClassSessionEdgeTest {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long groupId;

    @BeforeEach
    void seedACentre() {
        // الحصص أولاً: مفتاحها الأجنبي على المجموعات يمنع حذفها قبله
        sessionRepository.deleteAll();
        courseGroupRepository.deleteAll();
        teacherRepository.deleteAll();
        userRepository.deleteAll();

        save("admin", Role.ADMIN);

        Teacher teacher = new Teacher();
        teacher.setName("أستاذ رياضيات");
        teacher.setSubjectDefinition(subject("رياضيات"));
        teacher.setCommissionType("PERCENTAGE");
        teacher.setCommissionValue(new BigDecimal("50.00"));
        teacher = teacherRepository.save(teacher);

        CourseGroup group = CourseGroup.builder()
                .name("مجموعة الصباح")
                .teacher(teacher)
                .schoolLevel(SchoolLevel.SEC3)
                .maxCapacity(20)
                .sessionPrice(new BigDecimal("50.00"))
                .meetingDays(Set.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(18, 0))
                .autoName(false)
                .build();
        groupId = courseGroupRepository.save(group).getId();
    }

    @Test
    void aSessionIsOpenedAndThenClosedFromTheWeb() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/class-sessions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.groupName").value("مجموعة الصباح"))
                .andExpect(jsonPath("$.teacherName").value("أستاذ رياضيات"));

        MvcResult opened = mockMvc.perform(get("/api/class-sessions?open=true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        // Number لا Long: JsonPath يعيد Integer لرقمٍ صغير، وتحويلٌ مباشر يسقط
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                opened.getResponse().getContentAsString(), "$[0].id")).longValue();

        mockMvc.perform(post("/api/class-sessions/" + id + "/close").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());

        mockMvc.perform(get("/api/class-sessions?open=true").session(session))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * مجموعةٌ لها حصة مفتوحة لا تُفتح لها ثانية، والرسالة تقول ما يُفعل.
     *
     * <p>{@code 409} لا {@code 400}: حالٌ في القاعدة لا خطأٌ فيما كُتب - المطلوب إغلاق
     * القائمة، لا تصحيح الطلب.</p>
     */
    @Test
    void aGroupCannotHaveTwoOpenSessions() throws Exception {
        MockHttpSession session = signIn();
        String body = "{\"groupId\":" + groupId + "}";

        mockMvc.perform(post("/api/class-sessions").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/class-sessions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /** مجموعةٌ لا وجود لها: مدخلٌ خاطئ يصلحه من كتبه */
    @Test
    void anUnknownGroupIsRefusedAsBadInput() throws Exception {
        mockMvc.perform(post("/api/class-sessions").session(signIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(I18n.get("error.group.notFound")));
    }

    /** الحارس يعبر الحافة كما هو: فتحُ حصة كتابةٌ، فلا تُفتح بلا جلسة */
    @Test
    void openingASessionNeedsASession() throws Exception {
        mockMvc.perform(post("/api/class-sessions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + "}"))
                .andExpect(status().isUnauthorized());
    }

    /** جدول اليوم وملخّصه يخرجان من قراءة واحدة، فلا يتناقضان */
    @Test
    void theDaysTimetableCountsWhatItLists() throws Exception {
        MockHttpSession session = signIn();
        LocalDate saturday = LocalDate.of(2026, 9, 19);

        mockMvc.perform(get("/api/day-schedule?date=" + saturday).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(saturday.toString()))
                .andExpect(jsonPath("$.brief.total").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].groupName").value("مجموعة الصباح"));
    }

    private MockHttpSession signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centre\":\"\",\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
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
