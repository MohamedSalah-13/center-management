package com.codejava.center.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/**
 * الافتراضيات المشتركة لجداول الشاشات.
 *
 * <p>نص الجدول الفارغ ("No content in table") يأتي من حزمة JavaFX الداخلية، وهي لا تحوي
 * ترجمة عربية فيظهر بالإنجليزية داخل واجهة عربية كاملة. البديل الوحيد هو ضبط
 * {@code placeholder} على كل جدول.</p>
 *
 * <p>يُطبَّق مركزياً من {@link ViewLoader} لا في كل شاشة: النص واحد لثلاثة عشر جدولاً،
 * وإضافة جدول جديد لا يجوز أن تتطلب تذكّر هذا السطر.</p>
 */
public final class Tables {

    private Tables() {
    }

    /** يضبط نص "لا توجد بيانات" المترجَم على كل جدول داخل الشاشة */
    public static void localizeEmptyPlaceholders(Parent root) {
        for (Node node : root.lookupAll(".table-view")) {
            if (node instanceof TableView<?> table) {
                table.setPlaceholder(new Label(I18n.get("common.noData")));
            }
        }
    }
}
