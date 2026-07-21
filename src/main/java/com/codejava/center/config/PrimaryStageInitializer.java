package com.codejava.center.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PrimaryStageInitializer implements ApplicationListener<StageReadyEvent> {

    private final ApplicationContext applicationContext;

    // تم تغيير الملف المستهدف إلى شاشة الدخول
    @Value("classpath:/fxml/Login.fxml")
    private Resource loginFxml;

    public PrimaryStageInitializer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(loginFxml.getURL());
            fxmlLoader.setControllerFactory(applicationContext::getBean);

            Parent root = fxmlLoader.load();
            Stage stage = event.getStage();
            // تصغير أبعاد النافذة لتناسب شاشة تسجيل الدخول
            stage.setScene(new Scene(root, 500, 400));
            stage.setTitle("تسجيل الدخول - نظام إدارة السنتر");
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException("فشل في تحميل شاشة الدخول", e);
        }
    }
}