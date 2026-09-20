package com.codejava.center.config;

import com.codejava.center.core.ui.UiDispatcher;
import javafx.application.Platform;
import org.springframework.stereotype.Component;

/**
 * تركيب {@link UiDispatcher} على هذا الطرف: خيط تطبيق JavaFX.
 *
 * <p>سطرٌ واحد، وهو كل ما كان يربط {@code AlertFeed} بالواجهة الرسومية. وكونه هنا لا
 * هناك هو ما يجعل ذلك الصنف — وحساب منع التكرار الذي فيه — يعمل ويُختبر بلا نافذة،
 * وخادم البناء ليس فيه أدوات JavaFX مُقلعة أصلاً.</p>
 *
 * <p>{@link Platform#runLater} يرمي إن لم تكن الأدوات مُقلعة، ولا يقع ذلك هنا: لا
 * يُستدعى إلا بعد أن تُسجّل شاشةٌ نفسها، ولا شاشة قبل إقلاع الأدوات.</p>
 */
@Component
public class DesktopUiDispatcher implements UiDispatcher {

    @Override
    public void dispatch(Runnable task) {
        Platform.runLater(task);
    }
}
