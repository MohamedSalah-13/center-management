package com.codejava.center.util;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * تشغيل عمل قاعدة البيانات في الخلفية وتسليم النتيجة لخيط الواجهة.
 *
 * <p>يستبدل تكرار نمط CompletableFuture + Platform.runLater + exceptionally
 * في كل شاشة. الفائدة ليست الاختصار فقط: النسخ اليدوية كانت تنسى معالج الأخطاء
 * أحياناً فتبتلع الأعطال، وكانت تستدعي {@code ex.getCause()} مباشرةً وهو قد يكون null.</p>
 *
 * <p>يستخدم {@code whenComplete} لا {@code thenAccept} حتى يمرّ مسارا النجاح
 * والفشل كلاهما عبر خيط الواجهة ولا يضيع أي استثناء بصمت.</p>
 */
public final class FxAsync {

    private FxAsync() {
    }

    /** ينفّذ عملاً يُرجع قيمة في الخلفية، ثم يسلّمها لخيط الواجهة */
    public static <T> void supply(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        CompletableFuture.supplyAsync(work)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        onFailure.accept(rootCause(error));
                    } else {
                        onSuccess.accept(result);
                    }
                }));
    }

    /** ينفّذ عملاً بلا قيمة راجعة في الخلفية، ثم ينفّذ onSuccess على خيط الواجهة */
    public static void run(Runnable work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        supply(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }

    /**
     * يفكّ أغلفة CompletionException/ExecutionException للوصول إلى الاستثناء الحقيقي.
     * بدونها تصل الواجهة رسالة عامة بدل رسالة الخطأ المكتوبة بالعربية في الخدمة.
     */
    public static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** رسالة صالحة للعرض حتى لو كانت رسالة الاستثناء فارغة */
    public static String messageOf(Throwable error) {
        Throwable root = rootCause(error);
        return root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage()
                : root.getClass().getSimpleName();
    }
}
