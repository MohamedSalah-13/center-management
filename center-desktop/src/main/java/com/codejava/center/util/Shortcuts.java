package com.codejava.center.util;

import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ربط الاختصارات المحفوظة بمشهد الشاشة، وكشف تعارضها قبل أن يُحفظ.
 *
 * <p>الأوامر ({@link ShortcutAction}) لا تعرف كيف تُنفَّذ، والتنفيذ ({@code Runnable}) لا
 * يعرف بأي مفتاح يُستدعى. من يعرف الاثنين هو {@code DashboardController} وحده - فهو
 * صاحب التنقّل - فيسلّم خريطته مرة واحدة هنا، ويبقى كل ما عداها (القراءة، التعارض،
 * إعادة الربط بعد الحفظ) في هذا الصنف.</p>
 *
 * <p><b>الخريطة تُحفظ على المشهد نفسه</b> ({@code scene.getProperties()}) لا في حقل ساكن:
 * المتحكّم {@code PROTOTYPE} ويُعاد بناؤه مع كل تبديل لغة، فحقلٌ ساكن يحمل دوالّ نسخةٍ
 * هُجرت - تُغلق شاشتها ويبقى اختصارها يفتحها في نافذةٍ ماتت. وبالتعليق على المشهد تموت
 * الخريطة بموته. نفس علّة {@code AlertFeed} حين يملك مؤقّته بدل المتحكّم.</p>
 */
public final class Shortcuts {

    private static final String HANDLERS_KEY = "center.shortcuts.handlers";
    private static final String INSTALLED_KEY = "center.shortcuts.installed";

    private Shortcuts() {
    }

    /**
     * يسجّل ما يفعله كل أمر على هذا المشهد ثم يربط المحفوظ منها.
     *
     * @param handlers الأوامر المسموح بها لدور المستخدم الحالي وحدها
     */
    public static void install(Scene scene, Map<ShortcutAction, Runnable> handlers) {
        if (scene == null) {
            return;
        }
        // نسخة {@code EnumMap} لا الخريطة كما جاءت: ترتيب المرور عليها هو ترتيب الإعلان،
        // وهو ما يجعل "الأول يفوز" عند التكرار قراراً ثابتاً لا مصادفة
        Map<ShortcutAction, Runnable> copy = new EnumMap<>(ShortcutAction.class);
        copy.putAll(handlers);

        scene.getProperties().put(HANDLERS_KEY, copy);
        refresh(scene);
    }

    /**
     * يعيد قراءة المحفوظ ويربطه من جديد - يُستدعى بعد الحفظ في شاشة الاختصارات.
     *
     * <p>بدونه يعمل ما ضُبط بعد إعادة تشغيل البرنامج فقط، وهو ما يُقرأ "الشاشة لا تحفظ".</p>
     */
    @SuppressWarnings("unchecked")
    public static void refresh(Scene scene) {
        if (scene == null) {
            return;
        }
        Object stored = scene.getProperties().get(HANDLERS_KEY);
        if (!(stored instanceof Map)) {
            return;
        }
        Map<ShortcutAction, Runnable> handlers = (Map<ShortcutAction, Runnable>) stored;

        unbind(scene);

        Map<ShortcutAction, Shortcut> bindings = ShortcutPreferences.load();
        List<KeyCombination> installed = new ArrayList<>();

        for (Map.Entry<ShortcutAction, Runnable> entry : handlers.entrySet()) {
            Shortcut shortcut = bindings.get(entry.getKey());
            if (shortcut == null) {
                continue;
            }
            KeyCombination combination = shortcut.toCombination();

            // تركيبة سبق ربطها في هذه الجولة تُترك لصاحبها الأول: جدول اختصارات المشهد
            // خريطة، والوضع الثاني فوق نفس المفتاح يمحو الأول بلا خبر
            if (scene.getAccelerators().containsKey(combination)) {
                continue;
            }
            scene.getAccelerators().put(combination, entry.getValue());
            installed.add(combination);
        }

        scene.getProperties().put(INSTALLED_KEY, installed);
    }

    /**
     * يفكّ ربط الاختصارات مؤقتاً - وهو ما يجعل تسجيل اختصار جديد ممكناً أصلاً.
     *
     * <p>الشاشة تلتقط الضغطة لتعرف أي تركيبة اختار المستخدم، فلو بقي {@code Ctrl+S}
     * مربوطاً لفُتحت الإعدادات في اللحظة التي يحاول فيها إسناده إلى أمر آخر - أي لا سبيل
     * لإعادة ضبط اختصار مستعمَل إلا بمغادرة الشاشة. والفكّ أضمن من محاولة استهلاك الحدث:
     * ترتيب معالجة المشهد لاختصاراته أمرٌ داخليّ لا يعد به شيء.</p>
     *
     * <p>{@link #refresh(Scene)} هو ما يعيدها.</p>
     */
    public static void suspend(Scene scene) {
        unbind(scene);
    }

    @SuppressWarnings("unchecked")
    private static void unbind(Scene scene) {
        Object stored = scene.getProperties().get(INSTALLED_KEY);
        if (stored instanceof List) {
            ((List<KeyCombination>) stored).forEach(scene.getAccelerators()::remove);
        }
        scene.getProperties().put(INSTALLED_KEY, new ArrayList<KeyCombination>());
    }

    /**
     * الأمر الذي يحمل هذه التركيبة بالفعل، أو {@code null} إن كانت حرّة.
     *
     * <p>دالة نقية بلا JavaFX ولا سجلّ نظام، وهي قلب الميزة: تركيبة مكرَّرة تُقبَل في
     * الشاشة تعني أمرين على مفتاح واحد، وما ينفَّذ منهما يقرّره ترتيب المرور على جدول
     * الاختصارات - فيُقرأ الأمر عطلاً متقطّعاً لا خطأً في الإعداد.</p>
     *
     * <p>والبحث يجري في <b>كل</b> الأوامر لا فيما تعرضه الشاشة: السكرتير لا يرى أمر النسخ
     * الاحتياطي، فلو قُصر الفحص على المعروض لأسند {@code Ctrl+B} إلى شاشة الحضور، ثم جلس
     * المدير على نفس الجهاز فوجد التركيبتين حيّتين معاً.</p>
     *
     * @param bindings كل ما هو مسنَد الآن
     * @param action   الأمر الذي يُسنَد إليه (يُستثنى من الفحص - تركيبته الحالية ليست تعارضاً)
     */
    public static ShortcutAction conflictOf(Map<ShortcutAction, Shortcut> bindings,
                                            ShortcutAction action, Shortcut candidate) {
        if (candidate == null) {
            return null;
        }
        for (Map.Entry<ShortcutAction, Shortcut> entry : bindings.entrySet()) {
            if (entry.getKey() != action && candidate.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
