package com.codejava.center.util;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * شبكة الأمان للأخطاء التي خرجت من معالِج JavaFX بلا مسار فشل صريح.
 *
 * <p>لا تحل محل رسائل الخدمات المتوقعة؛ تلك تصل للمستخدم عبر {@link FxAsync}. هذه
 * للحالات البرمجية غير المتوقعة التي كانت تختفي في نسخة jpackage لعدم وجود طرفية.</p>
 */
@Component
public class UncaughtExceptionReporter {

    private static final Logger log = LoggerFactory.getLogger(UncaughtExceptionReporter.class);

    private final AtomicBoolean dialogPending = new AtomicBoolean();

    /** تُستدعى من خيط JavaFX قبل نشر أول حدث شاشة. */
    public void install() {
        Thread.UncaughtExceptionHandler handler = this::report;
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    void report(Thread thread, Throwable error) {
        Throwable cause = FxAsync.rootCause(error);
        log.error("خطأ غير معالج على الخيط {}", thread.getName(), cause);

        // أعطال الخلفية تُسجّل فقط؛ فتح نافذة من خيط مجدوِل أو ForkJoin غير آمن.
        if (!Platform.isFxApplicationThread() || !dialogPending.compareAndSet(false, true)) {
            return;
        }

        try {
            Platform.runLater(this::showFailureDialog);
        } catch (RuntimeException dialogSchedulingFailure) {
            dialogPending.set(false);
            log.error("تعذر جدولة نافذة الخطأ غير المعالج", dialogSchedulingFailure);
        }
    }

    private void showFailureDialog() {
        try {
            Dialogs.error(I18n.format("error.unexpectedLogged", ApplicationLogs.currentFile()));
        } catch (RuntimeException dialogFailure) {
            // لا نرمي من شبكة الأمان نفسها كي لا ندخل دورة أخطاء متتابعة.
            log.error("تعذر عرض نافذة الخطأ غير المعالج", dialogFailure);
        } finally {
            dialogPending.set(false);
        }
    }
}
