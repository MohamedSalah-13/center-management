package com.codejava.center.web;

import com.codejava.center.repository.UserRepository;
import com.codejava.center.service.InitialSetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * تهيئةُ المدير الأوّل من الويب.
 *
 * <p>هذه النقطةُ لم تكن موجودة، وغيابُها كان يعني أن خادماً لسنترٍ واحد على قاعدةٍ
 * جديدة <b>يعمل ولا يدخله أحد</b>: لا حساب، ولا سبيل إلى إنشاء واحد إلا بتوجيه البرنامج
 * المكتبي إلى القاعدة نفسها - أي أن نشر الخادم كان يحتاج إلى الجهاز.</p>
 *
 * <p>وما يُفحص هنا هو البرهانان معاً، لأن كلَّ واحدٍ وحده ثقب: <b>الرمز</b> بلا فحص
 * الفراغ بابُ إنشاءٍ دائم لمن قرأ متغيّرات التشغيل، و<b>الفراغ</b> بلا رمز يجعل أولَ
 * من يبلغ المنفذ مديرَ السنتر.</p>
 */
@SpringBootTest(properties = SetupEdgeTest.TOKEN_PROPERTY)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SetupEdgeTest {

    private static final String TOKEN = "deployer-only-setup-token";

    static final String TOKEN_PROPERTY = "CENTER_SETUP_TOKEN=" + TOKEN;

    private static final String STRONG = "Str0ng-Pass-9";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void emptyTheCentre() {
        userRepository.deleteAll();
    }

    /** قاعدةٌ جديدة تقول إنها تنتظر تهيئة، وإن الرمز مضبوط فالنموذج يُعرض */
    @Test
    void aFreshDatabaseAsksToBeSetUp() throws Exception {
        mockMvc.perform(get("/api/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.possible").value(true));
    }

    /**
     * <b>ولا يكفي أن يكون الجدول فارغاً.</b>
     *
     * <p>على جهازٍ في سنتر الفراغُ برهانٌ كافٍ - من يجلس أمام الجهاز الذي يحمل القاعدة
     * هو صاحبها. وعلى خادمٍ لا يبرهن شيئاً: يكفي أن يبلغ أحدُهم المنفذَ في النافذة بين
     * إنشاء القاعدة وأول تهيئة. وهو نفسُ السطر الذي وُجدت رموزُ الدعوة على المنصة
     * لأجله.</p>
     */
    @Test
    void anEmptyUsersTableIsNotOnItsOwnProofOfOwnership() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STRONG, STRONG)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.count()).isZero();
    }

    /** ورمزٌ خاطئ كغيابه: المقارنةُ في BearerToken، ولا فرق بين "قريب" و"بعيد" */
    @Test
    void aWrongTokenIsRefusedAndNothingIsCreated() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STRONG, STRONG)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.count()).isZero();
    }

    /** وبالرمز يُنشأ الحساب، ويُدخل به فعلاً - وذلك ما يجعل النقطة ميزةً لا صفّاً */
    @Test
    void theRightTokenCreatesAnAdminThatCanThenSignIn() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STRONG, STRONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value(InitialSetupService.INITIAL_ADMIN_USERNAME));

        mockMvc.perform(post("/api/session")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"centre":"","username":"%s","password":"%s"}
                                """.formatted(InitialSetupService.INITIAL_ADMIN_USERNAME, STRONG)))
                .andExpect(status().isOk());
    }

    /**
     * <b>والباب يُغلق بعد أوّل حساب، ولو بقي الرمزُ صحيحاً.</b>
     *
     * <p>الرمزُ يعيش في بيئة الخادم إلى ما بعد التهيئة، فلو كان وحده الحارس لبقي بابُ
     * إنشاء مديرين مفتوحاً لكل من قرأ متغيّرات التشغيل - وهي تُقرأ في سجلّ نشرٍ وفي
     * ملف خدمة. الفحصان معاً، لا أحدهما.</p>
     */
    @Test
    void theDoorClosesAfterTheFirstAdminEvenWithTheRightToken() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STRONG, STRONG)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("An0ther-Pass-9", "An0ther-Pass-9")))
                .andExpect(status().isConflict());

        assertThat(userRepository.count()).isOne();

        mockMvc.perform(get("/api/setup"))
                .andExpect(jsonPath("$.required").value(false));
    }

    /**
     * وسياسةُ كلمة المرور تسري هنا كما تسري في كل مكان.
     *
     * <p>حسابُ المدير الأوّل هو أقوى حسابٍ في السنتر، وكلمةٌ ضعيفة عليه أسوأ منها على
     * غيره. و400 لا 409: هذا مدخلٌ يُصلحه من أرسله.</p>
     */
    @Test
    void aWeakPasswordIsRefusedAndNoAdminIsLeftBehind() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123", "123")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    /** وتأكيدٌ لا يطابق يُرفض: كلمةٌ كُتبت بخطأٍ مطبعي تُقفل الحسابَ على صاحبه */
    @Test
    void aConfirmationThatDoesNotMatchIsRefused() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(STRONG, "Str0ng-Pass-8")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    /**
     * <b>واسمُ الحساب لا يأتي من الطلب.</b>
     *
     * <p>نفسُ قاعدة {@code "active":true} في تعديل الطالب: الحقلُ غيرُ معلَن في
     * {@code SetupRequest}، فما يُكتب في الجسم لا يصل إلى شيء. واسمٌ يختاره المتصل يعني
     * أن أوّل حسابٍ في السنتر قد لا يكون هو الذي تبحث عنه الوثائقُ ولا شاشةُ الدخول.</p>
     */
    @Test
    void aUsernameInTheBodyReachesNothing() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"root","password":"%s","confirmation":"%s"}
                                """.formatted(STRONG, STRONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value(InitialSetupService.INITIAL_ADMIN_USERNAME));

        assertThat(userRepository.findByUsername("root")).isEmpty();
    }

    private static String body(String password, String confirmation) {
        return """
                {"password":"%s","confirmation":"%s"}
                """.formatted(password, confirmation);
    }
}
