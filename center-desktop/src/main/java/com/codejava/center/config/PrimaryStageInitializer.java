package com.codejava.center.config;

import com.codejava.center.service.InitialSetupService;
import com.codejava.center.util.Dialogs;
import com.codejava.center.util.FxAsync;
import com.codejava.center.util.I18n;
import com.codejava.center.util.ViewLoader;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private final ViewLoader viewLoader;
    private final InitialSetupService initialSetupService;

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        // حتى استعلام COUNT البسيط قد ينتظر اتصال MySQL؛ لا نحجب خيط JavaFX عند الإقلاع.
        FxAsync.supply(initialSetupService::isSetupRequired,
                required -> showInitialScreen(event.getStage(), required),
                this::abortStartup);
    }

    private void showInitialScreen(Stage stage, boolean setupRequired) {
        try {
            // ViewLoader يتولى الحزمة النصية واتجاه الواجهة ومقاس النافذة،
            // وهو نفسه ما يُستدعى عند تبديل اللغة أو تسجيل الخروج
            if (setupRequired) {
                viewLoader.showInitialSetup(stage);
            } else {
                viewLoader.showLogin(stage);
            }
            stage.show();
        } catch (IOException e) {
            abortStartup(e);
        }
    }

    private void abortStartup(Throwable error) {
        Dialogs.error(I18n.format("error.initialScreenLoadFailed", FxAsync.messageOf(error)));
        Platform.exit();
    }
}
