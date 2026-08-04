package com.codejava.center.config;

import com.codejava.center.util.I18n;
import com.codejava.center.util.ViewLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private final ViewLoader viewLoader;

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            // ViewLoader يتولى الحزمة النصية واتجاه الواجهة ومقاس النافذة،
            // وهو نفسه ما يُستدعى عند تبديل اللغة أو تسجيل الخروج
            viewLoader.showLogin(event.getStage());
            event.getStage().show();
        } catch (IOException e) {
            throw new RuntimeException(I18n.get("error.loginLoadFailed"), e);
        }
    }
}
