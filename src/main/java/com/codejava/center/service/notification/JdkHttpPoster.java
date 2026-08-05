package com.codejava.center.service.notification;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * تنفيذ الطلب بعميل الـ HTTP المدمج في الـ JDK.
 *
 * <p>بلا مكتبة خارجية عن قصد: المشروع يشترط أن يُحلّ كل شيء من Maven Central وأن يبقى
 * خفيفاً، وإرسال طلب واحد لا يستدعي إضافة تبعية.</p>
 *
 * <p>المهلة مقصودة وقصيرة: الإرسال يجري على خيط خلفي، لكن الشاشة تنتظر نتيجته. مزوّد
 * لا يردّ يجب أن يظهر خطأً بعد ثوانٍ، لا أن يجمّد قائمة أربعين ولي أمر إلى ما لا نهاية.</p>
 */
@Component
public class JdkHttpPoster implements HttpPoster {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public Response post(String url, Map<String, String> headers, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        headers.forEach(request::header);

        HttpResponse<String> response = client.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }
}
