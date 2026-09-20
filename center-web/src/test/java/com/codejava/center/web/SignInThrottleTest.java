package com.codejava.center.web;

import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * الحاجز على العنوان، موصولاً فعلاً بشاشة الدخول.
 *
 * <p>{@link EdgeThrottleTest} يفحص العدّ والقفل؛ وهذا يفحص الشيء الآخر الذي يفشل
 * صامتاً: أن أحداً يستدعيه. حاجزٌ مكتوبٌ ولا يُستدعى يمرّ من كل اختبار وحدة ويترك
 * البابَ مفتوحاً.</p>
 *
 * <p><b>وفي سياقٍ خاص به عن قصد.</b> {@code EdgeThrottle} واحدٌ لكل سياق، وSpring
 * يشارك السياق بين أصناف الاختبار المتطابقة التهيئة. فاختبارٌ يستنفد العدّاد هنا كان
 * سيقفل العنوان على {@code ApiEdgeTest} بترتيبٍ يتغيّر بين تشغيلٍ وآخر - وهو أسوأ
 * أشكال التقطّع: فشلٌ لا يتكرّر. الخاصية أدناه تكفي ليكون له سياقه.</p>
 */
@SpringBootTest(properties = "center.test.context=sign-in-throttle")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SignInThrottleTest {

    @Autowired private MockMvc mockMvc;

    /**
     * أسماء مختلفة في كل محاولة - وهو الهجوم بعينه.
     *
     * <p>لا حساب من هؤلاء يبلغ خمس محاولات، فحاجزُ {@code AuthService} لا يراه أصلاً.
     * وبلا حاجز العنوان يمضي المهاجم على ألف اسم بكلمة مرور واحدة شائعة.</p>
     */
    @Test
    void manyNamesFromOneAddressAreStoppedEvenThoughNoAccountWasGuessedTwice() throws Exception {
        for (int attempt = 0; attempt < EdgeThrottle.MAX_FAILURES; attempt++) {
            mockMvc.perform(post("/api/session").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("user" + attempt)))
                    .andExpect(status().isBadRequest());
        }

        // 409 لا 400: ما يمنع المحاولة حالٌ في الخادم لا خطأٌ فيما كُتب، والفرق هو
        // الفرق بين "تحقّق مما أدخلت" و"انتظر ربع ساعة"
        //
        // والرسالة تُقارَن بصدر قالبها لا بنصّها كاملاً: عدد الدقائق المتبقّية يتناقص
        // بساعةٍ حقيقية بينما يجري الاختبار، فتثبيتُه يجعل الاختبار يسقط متى بطُؤ
        // الجهاز. والصدر يأتي من المفتاح نفسه لا من نصٍّ مكتوب هنا - القاعدة نفسها
        // التي تمنع توكيد نصٍّ يراه المستخدم حرفياً
        String template = I18n.get("error.auth.locked");
        String beforeTheNumber = template.substring(0, template.indexOf('{'));

        mockMvc.perform(post("/api/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("someone-else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith(beforeTheNumber)));
    }

    private String login(String username) {
        return """
                {"centre":"","username":"%s","password":"whatever"}
                """.formatted(username);
    }
}
