package com.codejava.center.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * بطاقة تنبيه تنزلق في زاوية النافذة ثم تختفي وحدها.
 *
 * <p>{@link Popup} لا {@code Stage}: النافذة المستقلة تظهر في شريط المهام وتسرق
 * التركيز من الحقل الذي يكتب فيه الموظف، فتضيع ضغطة مفتاح أو تُغلق قائمة منسدلة
 * مفتوحة. الـ Popup طبقةٌ فوق النافذة المالكة لا نافذة.</p>
 *
 * <p>والاتجاه يُضبط على محتوى البطاقة صراحةً من {@link ViewLoader#orientation()}:
 * الـ Popup ليس ابناً للمشهد فلا يرث اتجاهه، فكانت البطاقة العربية تُرصّ من اليسار
 * بينما البرنامج كله من اليمين.</p>
 *
 * <p>البطاقات تتراكم رأسياً بترتيب ظهورها، وتُعاد ترتيب الباقيات كلما اختفت واحدة -
 * وإلا تركت فجوةً في العمود، أو ظهرت التالية فوق مكان بطاقة لم تعد موجودة.</p>
 */
public final class Toasts {

    /** المدة قبل بدء الاختفاء. أطول من إشعار عادي: النصّ عربي وفيه اسم ومبلغ يُقرآن */
    private static final Duration VISIBLE = Duration.seconds(7);
    private static final Duration FADE = Duration.millis(280);

    /** عرض البطاقة قبل التكبير؛ ما يُستعمل فعلاً هو {@link #cardWidth()} */
    private static final double WIDTH = 340;
    private static final double MARGIN = 18;
    private static final double GAP = 10;

    /** البطاقات الظاهرة الآن، من الأقدم إلى الأحدث. تُلمس من خيط الواجهة وحده */
    private static final List<Popup> SHOWING = new ArrayList<>();

    /** سقف ما يظهر معاً؛ ما زاد يبقى في الصندوق بدل أن يغطّي الشاشة */
    private static final int MAX_VISIBLE = 4;

    private Toasts() {
    }

    /**
     * يعرض بطاقة فوق النافذة المالكة.
     *
     * <p>يُستدعى من خيط الواجهة وحده.</p>
     *
     * @param owner       النافذة المالكة؛ لا شيء يحدث إن كانت مغلقة أو غير ظاهرة
     * @param title       سطر العنوان (نوع التنبيه عادةً)
     * @param message     نصّ التنبيه
     * @param accentColor لون الشريط الجانبي، من درجة التنبيه
     * @param onClick     ما يُنفَّذ عند الضغط على البطاقة، أو {@code null}
     */
    public static void show(Window owner, String title, String message,
                            String accentColor, Runnable onClick) {
        // نافذة مغلقة أو مصغّرة: البطاقة ستُرسم في زاوية لا وجود لها
        if (owner == null || !owner.isShowing()) {
            return;
        }

        // الأقدم يفسح المكان: طابور بلا حدّ يغطّي الشاشة وما فوق الحدّ لا يُقرأ أصلاً
        while (SHOWING.size() >= MAX_VISIBLE) {
            SHOWING.get(0).hide();
            SHOWING.remove(0);
        }

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setHideOnEscape(true);
        popup.getContent().add(card(title, message, accentColor, popup, onClick));

        popup.show(owner);
        SHOWING.add(popup);
        layoutAll(owner);

        popup.setOnHidden(event -> {
            SHOWING.remove(popup);
            layoutAll(owner);
        });

        animate(popup);
    }

    /** يُخفي كل البطاقات - عند تسجيل الخروج أو إعادة بناء الشاشة */
    public static void hideAll() {
        for (Popup popup : List.copyOf(SHOWING)) {
            popup.hide();
        }
        SHOWING.clear();
    }

    private static Region card(String title, String message, String accentColor,
                               Popup popup, Runnable onClick) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1em; -fx-text-fill: #2c3e50;");
        titleLabel.setWrapText(true);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 0.93em; -fx-text-fill: #4a5568;");

        VBox text = new VBox(4, titleLabel, messageLabel);
        text.setPadding(new Insets(12, 14, 12, 14));
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);

        // شريط اللون هو ما يُقرأ قبل النص: الحرج يجب أن يُميَّز بلمحة
        Region accent = new Region();
        accent.setMinWidth(6);
        accent.setMaxWidth(6);
        accent.setStyle("-fx-background-color: " + accentColor + ";");

        HBox card = new HBox(accent, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(cardWidth());
        card.setMaxWidth(cardWidth());
        // حجم الخط على البطاقة نفسها لا على كل سطر فيها: الـ Popup مشهد مستقلّ لا يصله
        // سطر الجذر، وأبناء البطاقة يرثونه فتعمل الـ em أعلاه كما تعمل في ملف التنسيق
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8;"
                + " -fx-border-color: #dfe4ea; -fx-border-radius: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 12, 0, 0, 3);"
                + UiScale.fontSizeStyle());

        // الـ Popup ليس ابناً للمشهد فلا يرث اتجاهه؛ بدون هذا السطر تُرصّ
        // البطاقة العربية من اليسار والبرنامج كله من اليمين
        card.setNodeOrientation(ViewLoader.orientation());

        card.setOnMouseClicked(event -> {
            popup.hide();
            if (onClick != null) {
                onClick.run();
            }
        });
        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    /**
     * يرصّ البطاقات الظاهرة في الزاوية السفلية، الأحدث في الأسفل.
     *
     * <p>الزاوية تتبع اتجاه الواجهة: الإشعار يظهر عند "نهاية" السطر، وهي اليسار في
     * العربية واليمين في الإنجليزية.</p>
     */
    private static void layoutAll(Window owner) {
        double y = owner.getY() + owner.getHeight() - MARGIN;

        // من الأحدث إلى الأقدم، صعوداً من أسفل النافذة
        for (int i = SHOWING.size() - 1; i >= 0; i--) {
            Popup popup = SHOWING.get(i);
            double height = popup.getHeight() > 0 ? popup.getHeight() : 70;

            y -= height;
            popup.setY(y);
            popup.setX(I18n.isRightToLeft()
                    ? owner.getX() + MARGIN
                    : owner.getX() + owner.getWidth() - cardWidth() - MARGIN);
            y -= GAP;
        }
    }

    /**
     * عرض البطاقة بمقاس هذا الجهاز.
     *
     * <p>نصٌّ أكبر داخل عرض ثابت يزيد عدد الأسطر حتى تصير البطاقة عموداً ضيقاً طويلاً،
     * ولا بدّ أن يتبعه الموضع في {@code layoutAll} وإلا خرجت البطاقة من حافة النافذة.</p>
     */
    private static double cardWidth() {
        return UiScale.scaled(WIDTH);
    }

    private static void animate(Popup popup) {
        FadeTransition in = new FadeTransition(FADE, popup.getContent().get(0));
        in.setFromValue(0);
        in.setToValue(1);

        FadeTransition out = new FadeTransition(FADE, popup.getContent().get(0));
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(event -> popup.hide());

        new SequentialTransition(in, new PauseTransition(VISIBLE), out).play();
    }
}
