package com.codejava.center.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * حجم الخط - ومعه مقاس الواجهة كلها - على <b>هذا الجهاز</b>.
 *
 * <p>الفكرة كلها سطر واحد: البرنامج يضع {@code -fx-font-size} بالبكسل على جذر المشهد،
 * وكل مقاس في {@code style.css} مكتوب بـ {@code em} أي "نسبة من حجم خط العقدة". فحين
 * يتغيّر رقم الجذر تتبعه الخطوط والحشوات وأنصاف الأقطار وارتفاعات صفوف الجداول معاً،
 * ولا يوجد في البرنامج مكان ثانٍ يجب أن يتذكّره أحد. هذا أيضاً أسلوب {@code modena.css}
 * نفسها - ملف التنسيق الافتراضي في JavaFX - لا اختراع خاص بهذا المشروع.</p>
 *
 * <p><b>ولماذا لا {@code Scale} أو {@code setScaleX}؟</b> لأن التحويل الهندسي يكبّر صورةً
 * مرسومة: النص يخرج ضبابياً، وحدود العقد المحسوبة تبقى على مقاسها الأصلي فتتراكب مناطق
 * الضغط مع ما تراه العين. وتكبير ويندوز نفسه ({@code glass.win.uiScale}) قرارٌ على مستوى
 * النظام يحتاج إعادة تشغيل، بينما المطلوب هنا شاشة استقبال تُقرأ من مسافة متر وشاشة
 * مكتب تعرض أكبر قدر من صفوف الجدول - على نفس التركيب وفي نفس اللحظة.</p>
 *
 * <p><b>يُحفظ لكل جهاز</b> عبر {@link Preferences}، تماماً كاللغة والطابعة: المقاس صفة
 * الشاشة التي أمام المستخدم لا سياسة السنتر، وتيرمينال الاستقبال بشاشة 24 بوصة وجهاز
 * المدير المحمول لا يتفقان عليه. ولهذا - كعادة كل تفضيل جهاز هنا - لا ملف ترحيل Flyway له.</p>
 *
 * <p><b>الطباعة خارج هذا كله.</b> المطبوع يُقاس على الورق لا على الشاشة، و{@code Printing}
 * يبني عقده في مشهد خارج الشاشة لا يمرّ من هنا: فيبقى على {@code .root} الأساسي، وتظلّ
 * فواصل الصفحات المحسوبة صحيحة مهما كبّر المستخدم واجهته.</p>
 */
public final class UiScale {

    /** حجم الخط الأساس بالبكسل - المرجع الذي تُقاس عليه كل قيم {@code em} في التنسيق */
    public static final double BASE_FONT_SIZE = 14;

    /**
     * الدرجات المعروضة في قائمة الاختيار.
     *
     * <p>قائمة محدودة لا شريط انزلاق مستمر: الفرق بين 100% و103% لا يُرى، والمستخدم الذي
     * يبحث عن "أكبر" يحتاج خطوةً تُحدث فرقاً بضغطة واحدة. والحدّ الأعلى 175% لأن ما فوقه
     * يُخرج جداول الحضور عن أضيق شاشة يعمل عليها البرنامج.</p>
     */
    public static final double[] LEVELS = {0.85, 1.00, 1.15, 1.30, 1.50, 1.75};

    public static final double DEFAULT_FACTOR = 1.00;

    private static final double MIN_FACTOR = LEVELS[0];
    private static final double MAX_FACTOR = LEVELS[LEVELS.length - 1];

    /** ما يميّز قوائم اختيار الحجم في المشهد، ليُحدَّث عرضها بعد تغيير من لوحة المفاتيح */
    public static final String SELECTOR_STYLE_CLASS = "ui-scale-combo";

    private static final String PREF_KEY = "ui.fontScale";

    /** تقريب المقارنة بين عاملين: أرقام عشرية تُقرأ من السجل ولا تُقارَن بـ == */
    private static final double EPSILON = 0.001;

    private static volatile double factor = readPersisted();

    private UiScale() {
    }

    public static double factor() {
        return factor;
    }

    /** يضبط العامل ويحفظه على الجهاز. لا يلمس أي مشهد - التطبيق مسؤولية {@link #applyTo(Scene)} */
    public static void setFactor(double newFactor) {
        factor = clamp(newFactor);
        persist(factor);
    }

    /** حجم الخط بالبكسل المقابل للعامل الحالي */
    public static double fontSize() {
        return round(BASE_FONT_SIZE * factor);
    }

    /** يكبّر مقاساً مكتوباً بالبكسل في كود Java - لِما لا يصله التنسيق (عرض بطاقة، مقاس نافذة) */
    public static double scaled(double pixels) {
        return pixels * factor;
    }

    /**
     * قيمة {@code -fx-font-size} جاهزة للوضع في {@code setStyle}.
     *
     * <p>{@link Locale#ROOT} لا اللغة الحالية: محلل التنسيق في JavaFX يقرأ النقطة العشرية
     * وحدها، والعربية تكتبها فاصلة - فيُهمَل السطر كله بصمت ويعود الخط لحجمه الافتراضي.</p>
     */
    public static String fontSizeStyle() {
        return String.format(Locale.ROOT, "-fx-font-size: %.2fpx;", fontSize());
    }

    /** الدرجة التالية الأكبر، أو نفس العامل إن كان عند السقف. دالة نقية للاختبار */
    public static double next(double from) {
        for (double level : LEVELS) {
            if (level > from + EPSILON) {
                return level;
            }
        }
        return clamp(from);
    }

    /** الدرجة السابقة الأصغر، أو نفس العامل إن كان عند القاع. دالة نقية للاختبار */
    public static double previous(double from) {
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            if (LEVELS[i] < from - EPSILON) {
                return LEVELS[i];
            }
        }
        return clamp(from);
    }

    /** يردّ العامل إلى المدى المسموح. قيمة خارجه تعني سجلاً عُبث به أو إصداراً أحدث */
    public static double clamp(double value) {
        if (Double.isNaN(value)) {
            return DEFAULT_FACTOR;
        }
        return Math.min(MAX_FACTOR, Math.max(MIN_FACTOR, value));
    }

    /**
     * اسم الدرجة كما يُعرض: نسبة مئوية لا مفتاح ترجمة.
     * الرقم نفسه في اللغتين، و{@code NumberFormat} يتبع لغة الواجهة في موضع علامة %.
     */
    public static String label(double value) {
        return NumberFormat.getPercentInstance(I18n.current()).format(value);
    }

    /**
     * يطبّق العامل الحالي على مشهد: سطر واحد على الجذر يعيد تنسيق الشجرة كلها.
     *
     * <p>ويزامن معه قوائم اختيار الحجم الموجودة في نفس المشهد، لأن التغيير قد يأتي من
     * اختصار لوحة المفاتيح فتبقى القائمة الظاهرة في القائمة الجانبية تعرض رقماً قديماً.</p>
     */
    public static void applyTo(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        scene.getRoot().setStyle(fontSizeStyle());
        syncSelectors(scene);
        growToFit(scene);
    }

    /** يطبّق العامل ويربط اختصارات التكبير والتصغير بالمشهد */
    public static void install(Scene scene) {
        applyTo(scene);

        // EQUALS/MINUS هي مفاتيح الصف العلوي، وADD/SUBTRACT لوحة الأرقام، وPLUS لبعض
        // التخطيطات التي تُبلّغ عن + مباشرة. الثلاثة مطلوبة وإلا "لم يعمل الاختصار عندي"
        accelerator(scene, KeyCode.EQUALS, () -> change(scene, next(factor)));
        accelerator(scene, KeyCode.PLUS, () -> change(scene, next(factor)));
        accelerator(scene, KeyCode.ADD, () -> change(scene, next(factor)));

        accelerator(scene, KeyCode.MINUS, () -> change(scene, previous(factor)));
        accelerator(scene, KeyCode.SUBTRACT, () -> change(scene, previous(factor)));

        accelerator(scene, KeyCode.DIGIT0, () -> change(scene, DEFAULT_FACTOR));
        accelerator(scene, KeyCode.NUMPAD0, () -> change(scene, DEFAULT_FACTOR));
    }

    /** يبدّل العامل ويطبّقه فوراً على المشهد المعطى */
    public static void change(Scene scene, double newFactor) {
        setFactor(newFactor);
        applyTo(scene);
    }

    private static void accelerator(Scene scene, KeyCode key, Runnable action) {
        // SHORTCUT_DOWN = Ctrl على ويندوز، وهو ما يتوقعه أي مستخدم متصفّح
        scene.getAccelerators().put(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN), action);
    }

    /**
     * يحدّث قوائم اختيار الحجم داخل المشهد بعد تغيير جاء من مكان آخر.
     *
     * <p>البحث في المشهد لا سجلٌّ ساكن للقوائم: المتحكمات {@code PROTOTYPE} ويُعاد بناؤها
     * مع كل تبديل لغة، فقائمة ساكنة تحمل مراجع لقوائم شاشات أُغلقت من زمن.</p>
     */
    private static void syncSelectors(Scene scene) {
        for (Node node : scene.getRoot().lookupAll("." + SELECTOR_STYLE_CLASS)) {
            if (node instanceof ComboBox<?> combo) {
                @SuppressWarnings("unchecked")
                ComboBox<Double> typed = (ComboBox<Double>) combo;
                // الحارس ضد الدوران في UiScaleSelector يمنع أن يُعيد هذا الضبط إطلاق الحدث
                typed.setValue(factor);
            }
        }
    }

    /**
     * يوسّع النافذة إن ضاقت عن محتواها بعد التكبير - ولا يضيّقها أبداً.
     *
     * <p>شاشة الدخول 500×400 حول بطاقة واحدة: عند 175% يخرج زر الدخول من أسفلها ولا يبقى
     * ما يُضغط. والتوسيع محدود بمساحة الشاشة المرئية، وإلا صار العلاج نافذةً أكبر من
     * الشاشة لا يُرى نصفها.</p>
     */
    private static void growToFit(Scene scene) {
        Window window = scene.getWindow();
        if (!(window instanceof Stage stage) || stage.isMaximized() || !stage.isResizable()) {
            return;
        }

        scene.getRoot().applyCss();
        scene.getRoot().layout();

        // فرق إطار النافذة عن المشهد (شريط العنوان والحواف) يُضاف كما هو
        double chromeWidth = stage.getWidth() - scene.getWidth();
        double chromeHeight = stage.getHeight() - scene.getHeight();

        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double width = Math.min(scene.getRoot().prefWidth(-1) + chromeWidth, visual.getWidth());
        double height = Math.min(scene.getRoot().prefHeight(-1) + chromeHeight, visual.getHeight());

        if (width > stage.getWidth()) {
            stage.setWidth(width);
        }
        if (height > stage.getHeight()) {
            stage.setHeight(height);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double readPersisted() {
        try {
            return clamp(prefs().getDouble(PREF_KEY, DEFAULT_FACTOR));
        } catch (SecurityException e) {
            // بعض بيئات ويندوز المقيَّدة تمنع قراءة سجل المستخدم؛ الحجم الافتراضي يعمل على أي حال
            return DEFAULT_FACTOR;
        }
    }

    private static void persist(double value) {
        try {
            Preferences prefs = prefs();
            prefs.putDouble(PREF_KEY, value);
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            // فشل الحفظ يعني عودة الحجم للافتراضي بعد إعادة التشغيل فقط، لا يستحق إسقاط العملية
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(UiScale.class);
    }
}
