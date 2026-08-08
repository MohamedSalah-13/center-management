package com.codejava.center.util;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.ArrayList;
import java.util.List;

/**
 * تركيبة مفاتيح واحدة: مفتاح ومُعدِّلاته.
 *
 * <p><b>نوع خاص لا {@link KeyCombination} مباشرةً</b>، وهذا هو الفارق الذي يجعل بقية
 * الميزة قابلة للاختبار: {@code KeyCombination} تحلّ {@code SHORTCUT_DOWN} إلى Ctrl أو
 * Cmd بسؤال {@code Toolkit} عن المنصّة، فأي مقارنة أو نصّ عرض منها يحتاج تشغيل JavaFX -
 * وخادم البناء بلا واجهة أصلاً. هذا السجلّ لا يمسّ إلا {@link KeyCode} وهي enum عادية،
 * فالتحويل والتحقق وكشف التعارض كلها دوال نقية تُختبر بلا نافذة. نفس سبب بقاء
 * {@code Printing.pageBreaks} خالياً من JavaFX.</p>
 *
 * <p>وثلاثة مُعدِّلات لا أربعة: مفتاح ويندوز/Meta لا يصل التطبيق أصلاً على المنصّة التي
 * يعمل عليها البرنامج - النظام يلتقطه لنفسه - فعرضه في الشاشة يعني اختصاراً يُحفظ ولا
 * يعمل أبداً.</p>
 */
public record Shortcut(KeyCode code, boolean control, boolean shift, boolean alt) {

    private static final String SEPARATOR = "+";

    /** ما يُعرض بين المفاتيح: مسافة حول العلامة لأن "Ctrl+Shift+F5" تُقرأ كتلةً واحدة */
    private static final String DISPLAY_SEPARATOR = " + ";

    public static Shortcut control(KeyCode code) {
        return new Shortcut(code, true, false, false);
    }

    public static Shortcut controlShift(KeyCode code) {
        return new Shortcut(code, true, true, false);
    }

    /**
     * يقرأ ما حُفظ على الجهاز، أو {@code null} إن كان النصّ لا يدلّ على تركيبة.
     *
     * <p>لا استثناء: القيمة تأتي من سجلّ النظام وقد يكتبها إصدار أحدث أو تُعبَث يدوياً،
     * وسطرٌ لا يُفهم يعني اختصاراً واحداً مفقوداً لا برنامجاً لا يفتح.</p>
     */
    public static Shortcut parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        boolean control = false;
        boolean shift = false;
        boolean alt = false;
        KeyCode code = null;

        for (String part : text.trim().toUpperCase(java.util.Locale.ROOT).split("\\" + SEPARATOR)) {
            switch (part) {
                case "CTRL", "CONTROL" -> control = true;
                case "SHIFT" -> shift = true;
                case "ALT" -> alt = true;
                default -> code = keyCode(part);
            }
        }

        return code == null ? null : new Shortcut(code, control, shift, alt);
    }

    private static KeyCode keyCode(String name) {
        try {
            return KeyCode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** الصيغة المحفوظة على الجهاز: أسماء ثابتة لا تتغيّر بتغيّر لغة الواجهة */
    public String store() {
        return String.join(SEPARATOR, parts(true));
    }

    /**
     * ما يُكتب في الشاشة، مثل {@code Ctrl + Shift + F5}.
     *
     * <p>أسماء المُعدِّلات غير مترجَمة عن قصد: هي منقوشة على المفتاح نفسه بالإنجليزية في
     * كل لوحة مفاتيح يبيعها سوقٌ عربي، وترجمتها إلى "تحكّم" تجعل المستخدم يبحث في لوحته
     * عن مفتاح لا وجود له.</p>
     */
    public String display() {
        return String.join(DISPLAY_SEPARATOR, parts(false));
    }

    private List<String> parts(boolean forStorage) {
        List<String> parts = new ArrayList<>(4);
        if (control) {
            parts.add(forStorage ? "CTRL" : "Ctrl");
        }
        if (shift) {
            parts.add(forStorage ? "SHIFT" : "Shift");
        }
        if (alt) {
            parts.add(forStorage ? "ALT" : "Alt");
        }
        parts.add(forStorage ? code.name() : code.getName());
        return parts;
    }

    /**
     * هل تصلح هذه التركيبة اختصاراً أصلاً؟
     *
     * <p>شرطان، وكلاهما من عطبٍ حقيقي لا من ذوق: مفتاح وحده بلا مُعدِّل (حرف {@code س})
     * يعني أن كتابة اسم طالب في أي حقل تفتح شاشة أخرى في منتصف الكلمة؛ ومفتاح مُعدِّل
     * وحده ({@code Ctrl}) ليس اختصاراً بل نصفه، ويُحفظ ليعمل عند أول ضغطة على Ctrl مهما
     * كان ما بعدها.</p>
     *
     * <p>ومفاتيح الوظائف ({@code F1}…{@code F12}) مستثناة من شرط المُعدِّل: لا تكتب حرفاً
     * فلا تعترض أحداً وهو يكتب، وهي أسرع ما يُضغط على تيرمينال الاستقبال بيدٍ واحدة.</p>
     */
    public boolean isValid() {
        if (code == null || code.isModifierKey()) {
            return false;
        }
        return control || shift || alt || code.isFunctionKey();
    }

    /**
     * هل هذه التركيبة محجوزة لتكبير الواجهة؟
     *
     * <p>{@code UiScale} يربط {@code Ctrl} مع {@code +} و{@code -} و{@code 0} على كل مشهد
     * بما فيه شاشة الدخول. و{@code Scene} لا تمنع تركيبتين متطابقتين من السكن في جدول
     * اختصاراتها معاً - هي تمرّ عليها واحدة واحدة وتنفّذ أول ما يطابق - فالنتيجة أن أحد
     * الأمرين يعمل بترتيبٍ لا يضمنه شيء، والمستخدم يرى برنامجاً "يفتح شاشة عشوائية
     * أحياناً". الرفض عند الاختيار هو الموضع الوحيد الذي يمكن أن يُقال فيه السبب.</p>
     */
    public boolean isReserved() {
        if (!control || shift || alt) {
            return false;
        }
        for (KeyCode zoomKey : UiScale.ZOOM_KEYS) {
            if (zoomKey == code) {
                return true;
            }
        }
        return false;
    }

    /** التركيبة كما تفهمها JavaFX - نقطة الالتقاء الوحيدة مع {@code KeyCombination} */
    public KeyCodeCombination toCombination() {
        return new KeyCodeCombination(code,
                modifier(shift), modifier(control), modifier(alt),
                KeyCombination.ModifierValue.UP, KeyCombination.ModifierValue.UP);
    }

    private static KeyCombination.ModifierValue modifier(boolean pressed) {
        return pressed ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP;
    }
}
