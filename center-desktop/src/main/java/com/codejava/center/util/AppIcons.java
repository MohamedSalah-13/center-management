package com.codejava.center.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * أيقونة النافذة.
 *
 * <p>تُحمَّل مرة واحدة بكل مقاساتها؛ ويندوز يختار المقاس المناسب لشريط العنوان
 * وشريط المهام وعارض Alt+Tab، ولو أُعطي مقاساً واحداً فقط لتولّى التصغير بنفسه
 * وظهرت الحواف مهترئة.</p>
 *
 * <p>الصور مولّدة بـ {@code packaging/IconBuilder.java} من نفس الرسم الذي ينتج
 * {@code app.ico} الخاص بالملف التنفيذي، فالأيقونتان متطابقتان دائماً.</p>
 */
public final class AppIcons {

    private static final Logger log = LoggerFactory.getLogger(AppIcons.class);

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final String PATH = "/img/icon-%d.png";

    private static List<Image> icons;

    private AppIcons() {
    }

    public static synchronized List<Image> icons() {
        if (icons == null) {
            List<Image> loaded = new ArrayList<>();
            for (int size : SIZES) {
                String path = PATH.formatted(size);
                try (InputStream in = AppIcons.class.getResourceAsStream(path)) {
                    if (in == null) {
                        log.warn("Missing window icon resource: {}", path);
                        continue;
                    }
                    loaded.add(new Image(in));
                } catch (Exception e) {
                    log.warn("Unable to load window icon {}", path, e);
                }
            }
            icons = List.copyOf(loaded);
        }
        return icons;
    }

    public static void applyTo(Stage stage) {
        if (stage != null) {
            stage.getIcons().setAll(icons());
        }
    }

    /** يضع الأيقونة على نافذة حوار — النوافذ الفرعية لا ترثها عن مالكها. */
    public static void applyTo(Window window) {
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }
}
