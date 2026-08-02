package com.codejava.center.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * أدوات موحّدة للتعامل مع المبالغ المالية.
 * الهدف: ضمان أن كل مبلغ في النظام له نفس عدد الخانات العشرية ونفس طريقة التقريب،
 * وأن العرض في الواجهة لا يُظهر أرقاماً مثل 99.99000000000001
 */
public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private MoneyUtils() {
    }

    /** ضبط المبلغ على خانتين عشريتين، مع اعتبار null صفراً */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    /** تنسيق المبلغ للعرض في الواجهة (بدون صيغة أسّية) */
    public static String format(BigDecimal value) {
        return normalize(value).toPlainString();
    }

    /** تنسيق المبلغ مع إضافة العملة */
    public static String formatWithCurrency(BigDecimal value) {
        return format(value) + " ج.م";
    }
}
