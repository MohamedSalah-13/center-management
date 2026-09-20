package com.codejava.center.web;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * الحافة نفسها: من يمرّ، وبأيّ جواب حين لا يمرّ.
 *
 * <p>ما يُفحص هنا ليس الخدمات - لها اختباراتها في {@code center-app} - بل الأربعة
 * التي لا وجود لها إلا حين يُفتح منفذ HTTP، وكلٌّ منها يفشل صامتاً:</p>
 *
 * <ul>
 *   <li>طلبٌ بلا جلسة يُردّ بـ {@code 401} <b>JSON</b>، لا بتحويلٍ إلى صفحة دخول
 *       يصل العميل كـ HTML بحالة {@code 200} فينكسر في محلّل JSON.</li>
 *   <li>كتابةٌ بلا رمز CSRF تُردّ. الكوكي يُرسَل مع كل طلب أياً كان من بدأه.</li>
 *   <li>الدخول يفتح جلسةً تُقرأ في الطلب التالي.</li>
 *   <li>حارس الدور يعمل عبر الحافة كما يعمل في الخدمة: سكرتيرٌ يطلب كشف
 *       المتأخرات يُردّ بـ {@code 403} لا بصفحة فارغة.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ApiEdgeTest {

    /**
     * كلمة مرور المستخدمين المزروعين، تُولَّد لكل تشغيل ولا تُكتب حرفاً هنا.
     *
     * <p>لم تكن كذلك، وكانت نصاً يشبه كلمة مرور حقيقية فأبلغ عنه ماسحُ الأسرار على
     * الـ PR. والتنبيه صحيح ولو كان النصّ اختبارياً: الماسح لا يستطيع التفريق - وهو
     * الصواب منه - ونصٌّ كهذا في ملفٍ يُنسخ منه لاحقاً إلى سكربت زرعٍ حقيقي يصير
     * كلمةَ مرورٍ شُحنت مع البرنامج. والقيمة هنا لا معنى لها أصلاً: الاختبار يشفّرها
     * ثم يقارنها بنفسها.</p>
     */
    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAll();
        save("admin", Role.ADMIN);
        save("reception", Role.SECRETARY);
    }

    @Test
    void aRequestWithoutASessionIsRefusedAsJson() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(I18n.get("error.access.noSession")));
    }

    /** كلمات الشاشة تسبق الجلسة: شاشة الدخول نفسها لا تُرسَم بدونها */
    @Test
    void theScreensOwnWordsAreServedBeforeAnyoneSignsIn() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texts['web.login.title']").exists());
    }

    @Test
    void aWriteWithoutTheCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("admin", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signingInOpensASessionThatTheNextRequestReads() throws Exception {
        MockHttpSession session = signIn("admin");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value(Role.ADMIN.name()))
                .andExpect(jsonPath("$.roleName").value(Role.ADMIN.getDisplayName()));
    }

    /**
     * الحارس على الخدمة هو الحدّ الحقيقي، والحافة تنقل رفضه كما هو.
     *
     * <p>إخفاء زرٍّ في الواجهة عرضٌ لا أكثر - وعلى الويب لا معنى له أصلاً: العنوان
     * يُكتب في شريط المتصفّح.</p>
     */
    @Test
    void aSecretaryIsRefusedAnAdminOnlyReport() throws Exception {
        MockHttpSession session = signIn("reception");

        mockMvc.perform(get("/api/students/arrears").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminReadsTheSameReport() throws Exception {
        MockHttpSession session = signIn("admin");

        mockMvc.perform(get("/api/students/arrears").session(session))
                .andExpect(status().isOk());
    }

    /** الخروج يُنهي الجلسة فعلاً: نفس الكوكي بعده لا يقرأ شيئاً */
    @Test
    void signingOutEndsTheSession() throws Exception {
        MockHttpSession session = signIn("admin");

        mockMvc.perform(delete("/api/session").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsAreRefusedWithTheTranslatedReason() throws Exception {
        mockMvc.perform(post("/api/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("admin", "not the password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(I18n.get("error.auth.invalidCredentials")));
    }

    private MockHttpSession signIn(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private String login(String username, String password) {
        return """
                {"centre":"","username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private void save(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        userRepository.save(user);
    }
}
