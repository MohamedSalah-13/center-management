package com.codejava.center.web.api;

import com.codejava.center.util.I18n;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * نصوصُ الواجهة إلى المتصفّح.
 *
 * <p>القاعدة نفسها المكتوبة في {@code CLAUDE.md}: <b>لا نصّ يراه المستخدم داخل كود</b>.
 * وملفُّ JS كودٌ تماماً كملفِّ FXML، فلو كُتبت فيه العربية لَصار في البرنامج مصدران
 * للكلمة الواحدة: حزمةٌ تُترجم وتُراجَع، ونصوصٌ تتجمّد على لغة من كتبها.</p>
 *
 * <p>فالواجهة تسأل الخادم عن كلماتها بلغة الطلب، ويُخدَم السؤال قبل الدخول: شاشة
 * الدخول نفسها تحتاج كلماتها، ولا جلسة بعد.</p>
 *
 * <p>والمفاتيح المُسلَّمة هي بادئة {@code web.} وحدها. الحزمة أكثر من ألف مفتاح، وفي
 * أسمائها ما لا يخصّ المتصفّح - رسائلُ خدمات، وأسماءُ أعمدة تقارير - وتسليمها كلها
 * في كل تحميل نقلٌ بلا قارئ.</p>
 */
@RestController
@RequestMapping("/api")
public class MessagesController {

    /** ما يخصّ واجهة الويب وحدها */
    static final String WEB_PREFIX = "web.";

    public record Messages(String locale, boolean rightToLeft, Map<String, String> texts) {
    }

    @GetMapping("/messages")
    public Messages messages() {
        ResourceBundle bundle = I18n.bundle();
        Map<String, String> texts = new LinkedHashMap<>();
        for (String key : Collections.list(bundle.getKeys())) {
            if (key.startsWith(WEB_PREFIX)) {
                texts.put(key, bundle.getString(key));
            }
        }
        return new Messages(I18n.current().toLanguageTag(), I18n.isRightToLeft(), texts);
    }
}
