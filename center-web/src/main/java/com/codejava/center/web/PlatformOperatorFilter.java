package com.codejava.center.web;

import com.codejava.center.util.I18n;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * سطح المنصة مغلقٌ إلا لمن يحمل رمز مشغّلها.
 *
 * <p>الحراسة في فلتر لا في كل متحكّم عن قصد: فحصٌ يُكتب داخل الدوال ينسى كاتبُ الدالة
 * التالية أن يكتبه، فتصير نقطةٌ جديدة مفتوحةً للعالم بلا سطرٍ يقول ذلك. أمّا المسار
 * فيُحرس كلُّه دفعةً واحدة، ومن يضيف نقطةً تحته يجدها محروسة قبل أن يفكّر في ذلك.</p>
 *
 * <p><b>وثقبٌ واحدٌ مقصود:</b> تفعيلُ الدعوة. من يُفعّلها لا يملك رمز المشغّل ولا حساباً
 * بعد - هو صاحب السنتر الجديد، ودليلُه رمزُ الدعوة نفسه: مئة وستون بتاً عشوائية سُلِّمت
 * له خارج القناة. طلبُ رمزٍ ثانٍ منه يعني أن يُسلَّم رمز المنصة لكل عميل.</p>
 */
public class PlatformOperatorFilter extends OncePerRequestFilter {

    /** ما يحرسه هذا الفلتر */
    static final String PLATFORM_PATHS = "/api/platform/";

    /** والثقب الوحيد فيه */
    static final String REDEEM_PATH = "/api/platform/invites/redeem";

    private final PlatformOperator operator;

    public PlatformOperatorFilter(PlatformOperator operator) {
        this.operator = operator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(PLATFORM_PATHS) || REDEEM_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!operator.authorises(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            WebSecurityConfig.write(response, HttpStatus.UNAUTHORIZED,
                    I18n.get("error.platform.operatorOnly"));
            return;
        }
        chain.doFilter(request, response);
    }
}
