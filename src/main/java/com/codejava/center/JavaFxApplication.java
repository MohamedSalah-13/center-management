package com.codejava.center;

import com.codejava.center.config.StageReadyEvent;
import com.codejava.center.util.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        // لغة الواجهة المحفوظة تصبح Locale الافتراضي للـ JVM قبل إقلاع أي شيء:
        // أسماء الشهور في DatePicker ونصوص أزرار JavaFX الداخلية تقرأ الافتراضي وحده
        I18n.installAsJvmDefault();

        // تشغيل Spring Boot
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.applicationContext = new SpringApplicationBuilder()
                .sources(CenterApplication.class)
                // Spring Boot يضبط java.awt.headless=true افتراضياً، وإشعارات شريط
                // مهام ويندوز (TrayNotifier) من AWT: بدون هذا السطر ترمي
                // SystemTray.getSystemTray استثناء HeadlessException عند أول تنبيه.
                // وهو آمن هنا لأن البرنامج واجهة رسومية أصلاً ولا يعمل بلا شاشة.
                .headless(false)
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