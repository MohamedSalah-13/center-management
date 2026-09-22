package com.codejava.center.web;

import com.codejava.center.util.I18n;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.nio.charset.StandardCharsets;

/**
 * حافة HTTP: من يمرّ، وبأيّ دليل، وبأيّ جواب حين لا يمرّ.
 *
 * <h2>جلسة لا رمز JWT</h2>
 *
 * <p>ما يحمله الدليل هنا ليس الدور وحده بل <b>الدور والمؤسسة</b>، وكلاهما يتغيّر:
 * موظفٌ يُرقّى، واشتراكُ سنترٍ يتوقف. رمزٌ موقَّع يظلّ صحيحاً حتى ينتهي أجله، فيبقى
 * من فُصل يقرأ ومن توقف اشتراكه يكتب - إلى أن تُبنى قائمةُ إبطالٍ تُسأل عند كل طلب،
 * وهي الجلسةُ نفسها بمسمّى آخر. الجلسة تُبطَل في لحظتها، وتفتح {@code SseEmitter}
 * بلا آلة تجديدٍ خلفها، والعميل هنا متصفّحٌ على النطاق نفسه فلا حاجة إلى حملها
 * يدوياً في كل طلب.</p>
 *
 * <h2>وما دامت جلسةً على كوكيز، فـ CSRF حقيقي</h2>
 *
 * <p>الكوكي يُرسَل مع كل طلب إلى نطاقنا أياً كان من بدأه، فصفحةٌ في تبويب آخر تستطيع
 * أن تُطلق {@code POST} باسم من سجّل دخوله - إيصالٌ يُكتب أو مصروفٌ يُسجَّل بلا أن
 * يلمس أحد شيئاً. ولذلك الرمز في كوكي يقرؤه JS ويعيده في ترويسة: نطاقٌ آخر لا يستطيع
 * قراءة كوكينا، فلا يستطيع بناء الترويسة.</p>
 *
 * <p>و{@code CsrfTokenRequestAttributeHandler} صراحةً: المعالج الافتراضي يعمّي الرمز
 * لكل طلب (وقايةً من BREACH) فيخرج في الكوكي بصيغةٍ لا تطابق ما يُنتظر في الترويسة،
 * والنتيجة {@code 403} على كل كتابة مع كوكي يبدو موجوداً.</p>
 *
 * <h2>الرفض جوابٌ لا صفحة</h2>
 *
 * <p>الافتراضي تحويلٌ إلى صفحة دخول، وهو صحيحٌ لتطبيق صفحاتٍ كاملة. هنا العميل JS
 * يطلب JSON: تحويلٌ بـ {@code 302} يصله كـ HTML صفحةِ الدخول بحالة {@code 200}، فيقع
 * في محلّل JSON كخطأ صياغة - ويقرؤه المستخدم "خطأ غير متوقع" بدل "انتهت جلستك".</p>
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              SecurityContextRepository contexts,
                                              ObjectProvider<TenantBindingFilter> tenantBinding,
                                              ObjectProvider<PlatformOperatorFilter> platformGuard)
            throws Exception {

        CsrfTokenRequestAttributeHandler csrf = new CsrfTokenRequestAttributeHandler();

        http
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(protection -> protection
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrf)
                        // سطح المنصة معفى، وليس ذلك تساهلاً: الحماية من CSRF وُجدت لأن
                        // الكوكي يُرسَل تلقائياً مع كل طلب إلى نطاقنا أياً كان من بدأه.
                        // ولا شيء تلقائيّ هنا - رمزُ المشغّل يُكتب في ترويسة بيد من
                        // يطلب، ورمزُ الدعوة في جسم الطلب - فلا سلطةَ عابرة تُزوَّر
                        // والتهيئة معها ولنفس السبب بالضبط: سلطتُها رمزٌ يكتبه المتصل
                        // في ترويسة، لا كوكي يركب مع كل طلبٍ مهما بدأه. وما لا يُرسَل
                        // تلقائياً لا يُزوَّر من صفحةٍ في لسانٍ آخر
                        .ignoringRequestMatchers("/api/platform/**", "/api/setup"))
                .authorizeHttpRequests(requests -> requests
                        // الواجهة الساكنة وشاشة الدخول: لا تسأل القاعدة عن شيء
                        .requestMatchers("/", "/index.html", "/app.js", "/style.css",
                                "/favicon.ico").permitAll()
                        // كلمات الشاشة تسبق الجلسة: شاشة الدخول نفسها تحتاجها
                        .requestMatchers("/api/messages").permitAll()
                        // سطح المنصة لا يُحرس بجلسة سنتر - مشغّل المنصة ليس في أيّ
                        // سنتر. يحرسه PlatformOperatorFilter برمزه، وهو يعمل قبل هذا
                        .requestMatchers("/api/platform/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/session")
                        .permitAll()
                        // تهيئةُ المدير الأوّل تسبق وجودَ حسابٍ يُدخَل به، فلا جلسةَ
                        // تحرسها. يحرسها SetupToken برمزٍ من البيئة وبفراغ جدول
                        // المستخدمين معاً، وهي غيرُ موجودة أصلاً على منصة
                        .requestMatchers("/api/setup").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(WebSecurityConfig::unauthenticated)
                        .accessDeniedHandler((request, response, denied) ->
                                write(response, HttpStatus.FORBIDDEN,
                                        I18n.get("error.web.forbidden"))))
                // الخروج يملكه SessionController: هو الذي يكتب سطر سجل المراقبة،
                // ومعالجٌ ثانٍ يُنهي الجلسة قبله يترك الحدث بلا صاحب يُنسب إليه
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .anonymous(Customizer.withDefaults());

        // رمزُ CSRF كسولٌ في الإصدار السادس: لا يُكتب في الكوكي حتى يُقرأ، فطلبُ
        // قراءةٍ لا يلمسه يترك المتصفّح بلا رمز - ثم تُردّ أول كتابة بـ 403
        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        // الربط بالمؤسسة بعد تحميل سياق الأمان مباشرةً: قبله لا جلسة تُقرأ منها
        // المؤسسة، وبعد فلتر التصريح يكون المتحكّم قد بدأ عمله خارج أيّ نطاق
        tenantBinding.ifAvailable(filter ->
                http.addFilterAfter(filter, SecurityContextHolderFilter.class));

        // قبل فلتر التصريح: الرفض هنا يجب أن يقع قبل أن يُسأل عن جلسةٍ لا وجود لها
        platformGuard.ifAvailable(filter -> http.addFilterBefore(filter, CsrfFilter.class));

        return http.build();
    }

    /** يُجبر الرمز على التحقّق فيُكتب في الكوكي؛ لا يفعل شيئاً آخر */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    private static void unauthenticated(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException e) {
        write(response, HttpStatus.UNAUTHORIZED, I18n.get("error.access.noSession"));
    }

    /** شكلُ الرفض، مكتوبٌ مرة: يقرؤه العميل نفسه سواء جاء من هنا أو من حارس المنصة */
    static void write(jakarta.servlet.http.HttpServletResponse response,
                      HttpStatus status, String message) {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write("{\"status\":" + status.value() + ",\"message\":"
                    + quote(message) + "}");
        } catch (java.io.IOException ignored) {
            // العميل أغلق الاتصال قبل أن يُكتب الرفض؛ لا شيء يُقال له بعد ذلك
        }
    }

    /** تهريبٌ يدويّ: الرسالة قد تحمل علامة اقتباس، وسطرٌ مكسور يصل كـ JSON غير صالح */
    private static String quote(String message) {
        if (message == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : message.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
