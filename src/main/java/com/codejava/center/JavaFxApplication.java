package com.codejava.center;

import com.codejava.center.config.StageReadyEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        // تشغيل Spring Boot
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.applicationContext = new SpringApplicationBuilder()
                .sources(CenterApplication.class)
                .run(args);
    }

    @Override
    public void start(Stage stage) {
        // إخبار Spring أن الـ Stage جاهز للاستخدام
        this.applicationContext.publishEvent(new StageReadyEvent(stage));
    }

    @Override
    public void stop() {
        // إغلاق النظامين معاً بأمان
        this.applicationContext.close();
        Platform.exit();
    }
}