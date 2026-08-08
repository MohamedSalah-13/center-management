package com.codejava.center.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * اختصارات لوحة المفاتيح على <b>هذا الجهاز</b>.
 *
 * <p>تفضيل جهاز لا إعداد سنتر، كاللغة وحجم الخط والطابعة: لوحة المفاتيح صفة التيرمينال
 * الذي أمام المستخدم. تيرمينال البوابة يُدار بيد واحدة والأخرى تمسك القارئ، وجهاز المدير
 * لوحته كاملة - وليس بين الجهازين ما يوجب أن يتفقا. وككل تفضيل جهاز هنا: لا ملف ترحيل
 * Flyway له.</p>
 *
 * <p><b>وقيمة فارغة ليست قيمةً غائبة.</b> السجلّ يميّز بين ثلاث حالات لا اثنتين: مفتاح
 * غير مكتوب أصلاً يعني "لم يُمسّ" فيأخذ افتراضي البرنامج، ومفتاح مكتوب بنصّ فارغ يعني
 * "مُسح عمداً" فيبقى بلا اختصار. ولولا التفريق لكان كل من مسح {@code Ctrl+S} يجده عائداً
 * في أول تشغيل تالٍ، وهو بالضبط ما يُقرأ عطلاً في البرنامج لا سياسةً فيه.</p>
 */
public final class ShortcutPreferences {

    private static final String KEY_PREFIX = "shortcut.";

    /** ما يُكتب مكان التركيبة ليعني "مُسح عمداً" - راجع تعليق الصنف */
    private static final String CLEARED = "";

    private ShortcutPreferences() {
    }

    /**
     * الاختصارات السارية: المحفوظ على الجهاز فوق افتراضيات البرنامج.
     *
     * <p>ما لا يصلح اختصاراً يُسقَط بصمت هنا: القيمة تأتي من سجلّ النظام، وقد يكتب فيها
     * إصدارٌ أحدث اسم مفتاح لا تعرفه هذه النسخة. أمرٌ بلا اختصار أهون من شاشة لا تُفتح.</p>
     */
    public static Map<ShortcutAction, Shortcut> load() {
        Map<ShortcutAction, Shortcut> bindings = new EnumMap<>(ShortcutAction.class);

        for (ShortcutAction action : ShortcutAction.values()) {
            Shortcut shortcut = read(action);
            if (shortcut != null && shortcut.isValid() && !shortcut.isReserved()) {
                bindings.put(action, shortcut);
            }
        }
        return bindings;
    }

    /** الافتراضيات وحدها، لزرّ "استعادة الافتراضي" في الشاشة */
    public static Map<ShortcutAction, Shortcut> defaults() {
        Map<ShortcutAction, Shortcut> bindings = new EnumMap<>(ShortcutAction.class);

        for (ShortcutAction action : ShortcutAction.values()) {
            if (action.getDefaultShortcut() != null) {
                bindings.put(action, action.getDefaultShortcut());
            }
        }
        return bindings;
    }

    /**
     * يحفظ الأوامر المذكورة في الخريطة وحدها.
     *
     * <p>الجزئية مقصودة: الشاشة لا تعرض إلا ما يسمح به دور من فتحها، وحفظٌ يمسح ما لم
     * يُعرَض كان يعني أن دخول السكرتير مرةً واحدة يمحو اختصارات المدير كلها من الجهاز.</p>
     *
     * @param bindings الأمر إلى تركيبته، و{@code null} للأمر الذي مُسح اختصاره
     */
    public static void save(Map<ShortcutAction, Shortcut> bindings) {
        try {
            Preferences prefs = prefs();
            for (Map.Entry<ShortcutAction, Shortcut> entry : bindings.entrySet()) {
                prefs.put(KEY_PREFIX + entry.getKey().name(),
                        entry.getValue() == null ? CLEARED : entry.getValue().store());
            }
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.shortcuts.notSaved"), e);
        }
    }

    /** يعيد الجهاز إلى افتراضيات البرنامج: يمحو المكتوب فلا يبقى حتى "المسح العمد" */
    public static void resetAll() {
        try {
            Preferences prefs = prefs();
            for (ShortcutAction action : ShortcutAction.values()) {
                prefs.remove(KEY_PREFIX + action.name());
            }
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            throw new IllegalStateException(I18n.get("error.shortcuts.notSaved"), e);
        }
    }

    private static Shortcut read(ShortcutAction action) {
        String saved;
        try {
            saved = prefs().get(KEY_PREFIX + action.name(), null);
        } catch (SecurityException e) {
            // بيئة ويندوز مقيَّدة تمنع قراءة سجل المستخدم: الافتراضيات تعمل على أي حال
            return action.getDefaultShortcut();
        }

        if (saved == null) {
            return action.getDefaultShortcut();
        }
        return saved.isEmpty() ? null : Shortcut.parse(saved);
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(ShortcutPreferences.class);
    }
}
