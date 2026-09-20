package com.codejava.center.web;

import com.codejava.center.core.i18n.LocaleProvider;
import com.codejava.center.util.I18n;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * لغةُ <b>هذا الطلب</b>: جوابُ الخادم عن {@code LocaleProvider}.
 *
 * <p>على الجهاز تُقرأ اللغة من تفضيلٍ محفوظ في السجلّ، لأن الجهاز أمام إنسان واحد.
 * وعلى خادمٍ يقرأ منه عشرةٌ في اللحظة نفسها لا معنى لـ"لغة البرنامج": من يفتح الشاشة
 * بالعربية ومن يفتحها بالإنجليزية طلبان متزامنان، ولذلك المصدر هنا
 * {@link LocaleContextHolder} الذي يملؤه Spring من ترويسة {@code Accept-Language}.</p>
 *
 * <p>وهذا بالضبط ما يجعل {@code I18n.current()} <b>استدعاءً لا حقلاً يُقرأ مرة</b>،
 * ويجعل الحزم مخزَّنةً لكل لغة لا حزمةً "حالية" واحدة.</p>
 *
 * <p>واللغة تُقصر على ما يفهمه البرنامج: ترويسةٌ تطلب الفرنسية لا تعني حزمةً فرنسية
 * بل العربية - وهي الحزمة الأساس. والبديل ترك {@code Locale} كما جاء، فتُبنى منه
 * أسماءُ شهورٍ وصيغُ أرقامٍ بلغة لا نصّ فيها لها.</p>
 */
@Component
public class ServerLocaleProvider implements LocaleProvider {

    @Override
    public Locale current() {
        Locale requested = LocaleContextHolder.getLocale();
        return requested != null && I18n.ENGLISH.getLanguage().equals(requested.getLanguage())
                ? I18n.ENGLISH
                : I18n.ARABIC;
    }

    /**
     * يُركَّب ثابتاً لا يُحقَن، لأن {@code I18n} كذلك: الـ enums تعرض
     * {@code getDisplayName()} ولا تقبل حقناً، والخدمات تعمل على خيوط مجمّع.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        I18n.install(this);
    }
}
