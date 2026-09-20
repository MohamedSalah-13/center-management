package com.codejava.center.util;

import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * لغةُ هذا الجهاز: جوابُ سطح المكتب عن سؤال {@code LocaleProvider} في النواة.
 *
 * <p><b>محفوظة لكل جهاز</b> عبر {@link Preferences} لا في {@code CenterSettings}: شاشة
 * الدخول تحتاج اللغة قبل أن يكون هناك مستخدم أو جلسة، وتيرمينالات السنتر الواحد قد
 * تخدم مشغّلين مختلفين. ولهذا لا ملف ترحيل Flyway لهذه الميزة - كالطابعة وحجم الخط
 * والاختصارات.</p>
 *
 * <p>وهو في الحزمة التي كانت فيها {@code I18n} وبالمفتاح نفسه عن قصد:
 * {@link Preferences#userNodeForPackage} تُفهرس بالحزمة لا بالصنف، فانتقال القراءة من
 * صنف إلى صنف في حزمةٍ أخرى كان يعني أن كل عميل اختار الإنجليزية يجدها عادت عربية بعد
 * التحديث، بلا أن يقول شيءٌ لماذا.</p>
 *
 * <p>ساكنٌ لا bean، ويُركَّب بـ {@code I18n.install} لا بالحقن، للسبب الذي يجعل
 * {@code I18n} نفسها ساكنة: الـ enums تحتاج اسماً مترجَماً وهي لا تقبل الحقن، والخدمات
 * تُنفَّذ على خيوط ForkJoinPool. الواجهة في النواة لتُجاب من مكان آخر يوماً، لا لتُحقن
 * اليوم.</p>
 */
public final class LanguagePreferences {

    private static final String PREF_KEY = "ui.language";

    private static volatile Locale current = readPersisted();

    private LanguagePreferences() {
    }

    /**
     * يجعل تفضيلَ هذا الجهاز هو مصدرَ اللغة لطبقة الأعمال، ويضبط افتراضي الـ JVM معه.
     *
     * <p>يُستدعى من {@code JavaFxApplication.init()}: قبل إقلاع Spring وقبل رسم أول
     * شاشة. ولو نُسي لظلّ البرنامج عربياً دائماً بلا خطأ في أي سجل - وهو تماماً نوع
     * العطل الذي يُبلَّغ عنه بأن "زرّ اللغة لا يعمل".</p>
     */
    public static void install() {
        I18n.install(LanguagePreferences::current);
        installAsJvmDefault();
    }

    public static Locale current() {
        return current;
    }

    /**
     * تبديل لغة الواجهة: يحفظ الاختيار، ويضبط افتراضي الـ JVM.
     *
     * <p>لا تُعيد هذه الدالة بناء الشاشة المعروضة؛ ذلك من مسؤولية
     * {@code ViewLoader.reloadScene} لأنه وحده يملك الـ Stage.</p>
     */
    public static void set(Locale locale) {
        current = locale;
        installAsJvmDefault();
        persist(locale);
    }

    /**
     * يجعل لغة الواجهة هي Locale الافتراضي للـ JVM.
     *
     * <p>ضروري لا تجميلي: أسماء الشهور في {@code DatePicker}، ونصوص أزرار
     * {@code ButtonType} (موافق/إلغاء)، وقائمة {@code TableView} السياقية كلها تأتي من
     * حزم JavaFX الداخلية التي تقرأ {@link Locale#getDefault()} ولا تعرف شيئاً عن
     * حزمتنا النصية.</p>
     *
     * <p>وهو هنا لا في {@code I18n} لأن ضبط الافتراضي أثرٌ على الـ JVM كاملاً: يصحّ لمن
     * يملك الـ JVM - برنامجٌ أمام إنسان واحد - ولا يصحّ لخادمٍ يخدم سناتر بلغات
     * مختلفة في اللحظة نفسها.</p>
     */
    private static void installAsJvmDefault() {
        Locale.setDefault(current);
    }

    private static Locale readPersisted() {
        try {
            String tag = prefs().get(PREF_KEY, null);
            if (tag != null && "en".equals(Locale.forLanguageTag(tag).getLanguage())) {
                return I18n.ENGLISH;
            }
        } catch (SecurityException e) {
            // بعض بيئات ويندوز المقيَّدة تمنع قراءة سجل المستخدم؛ العربية هي الافتراضي على أي حال
        }
        return I18n.ARABIC;
    }

    private static void persist(Locale locale) {
        try {
            Preferences prefs = prefs();
            prefs.put(PREF_KEY, locale.getLanguage());
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // فشل الحفظ يعني أن اللغة تعود للعربية بعد إعادة التشغيل فقط، لا يستحق إسقاط العملية
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(LanguagePreferences.class);
    }
}
