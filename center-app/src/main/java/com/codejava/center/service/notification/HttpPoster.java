package com.codejava.center.service.notification;

import java.util.Map;

/**
 * طلب HTTP واحد، مفصولاً عن المرسلات لتُختبر بلا شبكة.
 *
 * <p>الاختبار هنا ليس ترفاً: ما يُبنى في {@link WhatsAppCloudApiSender} و
 * {@link HttpGatewaySender} هو نصّ الطلب — رقم في الموضع الخطأ أو نص غير مهروب — وهو
 * خطأ لا يظهر إلا في رسالة تصل ولي أمر ناقصة أو لا تصل.</p>
 */
public interface HttpPoster {

    /**
     * @return استجابة المزوّد كما جاءت؛ الحكم على النجاح لمن يستدعي
     * @throws Exception عند تعذّر الوصول أصلاً (لا إنترنت، عنوان خاطئ، انتهاء المهلة)
     */
    Response post(String url, Map<String, String> headers, String body) throws Exception;

    record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }
}
