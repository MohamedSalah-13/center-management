package com.codejava.center.util;

import com.codejava.center.domain.enums.Role;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * القرارات التي تحكم اختصارات لوحة المفاتيح، بلا JavaFX ولا سجلّ نظام.
 *
 * <p>نفس سبب وجود {@code PaginationTest} و{@code UiScaleTest}: هذه دوال نقية وخطؤها لا
 * يظهر إلا على جهاز العميل - وبأسوأ صورة ممكنة، إذ لا شيء يُعلن عن نفسه. تركيبة مكرَّرة
 * تعني أمرين تحت مفتاح واحد فينفَّذ أحدهما بترتيبٍ لا يضمنه شيء، وتركيبة بلا مُعدِّل
 * تعني شاشةً تُفتح في منتصف كتابة اسم طالب. كلاهما يُقرأ "البرنامج يتصرّف من نفسه".</p>
 */
class ShortcutsTest {

    @Test
    void storedTextSurvivesTheRoundTrip() {
        Shortcut shortcut = new Shortcut(KeyCode.S, true, true, true);

        assertThat(Shortcut.parse(shortcut.store())).isEqualTo(shortcut);
        assertThat(Shortcut.parse(Shortcut.control(KeyCode.B).store()))
                .isEqualTo(Shortcut.control(KeyCode.B));
    }

    /** القيمة تأتي من سجلّ النظام: إصدار أحدث أو عبث يدوي لا يجوز أن يمنع البرنامج من الفتح */
    @Test
    void unreadableStoredTextBecomesNoShortcutRatherThanAnException() {
        assertThat(Shortcut.parse(null)).isNull();
        assertThat(Shortcut.parse("")).isNull();
        assertThat(Shortcut.parse("CTRL")).isNull();
        assertThat(Shortcut.parse("CTRL+NOT_A_KEY")).isNull();
    }

    /** ما حُفظ بأسماء ثابتة يُقرأ بأي حالة أحرف؛ الأسماء المعروضة شيء آخر لا يُقرأ منه */
    @Test
    void storedTextIsCaseInsensitiveAndDisplayTextNamesTheKeysOnTheKeyboard() {
        assertThat(Shortcut.parse("ctrl+shift+f5"))
                .isEqualTo(new Shortcut(KeyCode.F5, true, true, false));

        assertThat(Shortcut.control(KeyCode.S).display()).isEqualTo("Ctrl + S");
        assertThat(new Shortcut(KeyCode.F5, true, true, false).display())
                .isEqualTo("Ctrl + Shift + F5");
    }

    /**
     * مفتاح وحده ليس اختصاراً: كتابة اسم طالب في أي حقل كانت ستفتح شاشة أخرى.
     * ومفاتيح الوظائف مستثناة لأنها لا تكتب حرفاً أصلاً.
     */
    @Test
    void aShortcutNeedsAModifierUnlessItIsAFunctionKey() {
        assertThat(new Shortcut(KeyCode.S, false, false, false).isValid()).isFalse();
        assertThat(new Shortcut(KeyCode.CONTROL, true, false, false).isValid()).isFalse();

        assertThat(Shortcut.control(KeyCode.S).isValid()).isTrue();
        assertThat(new Shortcut(KeyCode.F5, false, false, false).isValid()).isTrue();
        assertThat(new Shortcut(KeyCode.S, false, false, true).isValid()).isTrue();
    }

    /**
     * {@code UiScale} يربط Ctrl مع + و- و0 على كل مشهد بما فيه شاشة الدخول، والمشهد لا
     * يعترض على تركيبتين متطابقتين في جدوله - ينفّذ أول ما يطابق. فالرفض هنا هو ما يمنع
     * برنامجاً "يفتح شاشة عشوائية أحياناً".
     */
    @Test
    void theZoomCombinationsCannotBeTakenByAnyCommand() {
        for (KeyCode zoomKey : UiScale.ZOOM_KEYS) {
            assertThat(Shortcut.control(zoomKey).isReserved())
                    .as("Ctrl + " + zoomKey)
                    .isTrue();
        }

        // بمُعدِّل آخر تصير تركيبة أخرى لا يحجزها التكبير
        assertThat(Shortcut.controlShift(KeyCode.DIGIT0).isReserved()).isFalse();
        assertThat(Shortcut.control(KeyCode.S).isReserved()).isFalse();
    }

    @Test
    void aCombinationHeldByAnotherCommandIsReportedByName() {
        Map<ShortcutAction, Shortcut> bindings = new EnumMap<>(ShortcutAction.class);
        bindings.put(ShortcutAction.SETTINGS, Shortcut.control(KeyCode.S));
        bindings.put(ShortcutAction.BACKUP_NOW, Shortcut.control(KeyCode.B));

        assertThat(Shortcuts.conflictOf(bindings, ShortcutAction.ATTENDANCE, Shortcut.control(KeyCode.S)))
                .isEqualTo(ShortcutAction.SETTINGS);
        assertThat(Shortcuts.conflictOf(bindings, ShortcutAction.ATTENDANCE, Shortcut.control(KeyCode.M)))
                .isNull();
    }

    /** إعادة إسناد الأمر تركيبته الحالية ليست تعارضاً، وإلا استحال حفظ الصف كما هو */
    @Test
    void aCommandDoesNotConflictWithItself() {
        Map<ShortcutAction, Shortcut> bindings = new EnumMap<>(ShortcutAction.class);
        bindings.put(ShortcutAction.SETTINGS, Shortcut.control(KeyCode.S));

        assertThat(Shortcuts.conflictOf(bindings, ShortcutAction.SETTINGS, Shortcut.control(KeyCode.S)))
                .isNull();
    }

    /**
     * الفحص يجري على كل الأوامر لا على ما تعرضه الشاشة: السكرتير لا يرى أمر النسخ
     * الاحتياطي، فلولا ذلك لأسند {@code Ctrl+B} إلى شاشة الحضور، ثم جلس المدير على نفس
     * الجهاز فوجد التركيبتين حيّتين معاً.
     */
    @Test
    void aCommandHiddenFromTheCurrentRoleStillHoldsItsCombination() {
        Map<ShortcutAction, Shortcut> bindings = ShortcutPreferences.defaults();

        assertThat(ShortcutAction.BACKUP_NOW.isAllowedFor(Role.SECRETARY)).isFalse();
        assertThat(Shortcuts.conflictOf(bindings, ShortcutAction.ATTENDANCE,
                ShortcutAction.BACKUP_NOW.getDefaultShortcut()))
                .isEqualTo(ShortcutAction.BACKUP_NOW);
    }

    /**
     * الافتراضيات نفسها لا يجوز أن تتعارض ولا أن تمسّ محجوزاً: تصادمُ افتراضيَّين لا
     * يكتشفه أحد - البرنامج يُثبَّت وأحد الأمرين لا يعمل من أول يوم، ولا شاشة تقول لماذا.
     */
    @Test
    void theShippedDefaultsAreValidUniqueAndFree() {
        Map<Shortcut, ShortcutAction> seen = new HashMap<>();

        for (ShortcutAction action : ShortcutAction.values()) {
            Shortcut shortcut = action.getDefaultShortcut();
            if (shortcut == null) {
                continue;
            }

            assertThat(shortcut.isValid()).as(action + " -> " + shortcut.display()).isTrue();
            assertThat(shortcut.isReserved()).as(action + " -> " + shortcut.display()).isFalse();
            assertThat(seen.put(shortcut, action))
                    .as(action + " تحمل تركيبة مسنَدة أصلاً: " + shortcut.display())
                    .isNull();
        }
    }

    /** الأدوار: كل ما يخصّ المال أو الإدارة لا يصل إليه اختصار على جهاز يجلس عليه سكرتير */
    @Test
    void financeAndAdminCommandsAreAdminOnly() {
        assertThat(ShortcutAction.ATTENDANCE.isAllowedFor(Role.SECRETARY)).isTrue();
        assertThat(ShortcutAction.ATTENDANCE.isAllowedFor(Role.ADMIN)).isTrue();

        assertThat(ShortcutAction.CASHIER.isAllowedFor(Role.SECRETARY)).isFalse();
        assertThat(ShortcutAction.SETTINGS.isAllowedFor(Role.SECRETARY)).isFalse();
        assertThat(ShortcutAction.SETTINGS.isAllowedFor(Role.ADMIN)).isTrue();

        // بلا جلسة لا اختصار: لا شيء يُربط قبل تسجيل الدخول
        assertThat(ShortcutAction.HOME.isAllowedFor(null)).isFalse();
    }
}
