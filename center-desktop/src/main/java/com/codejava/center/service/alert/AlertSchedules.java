package com.codejava.center.service.alert;

import com.codejava.center.core.alert.AlertSchedule;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.util.I18n;

/**
 * ما يحتاجه {@link AlertSchedule} من هذا التطبيق: صفُّ الإعدادات، وحزمةُ النصوص.
 * نفس فصل {@code BackupSchedules} ولنفس السبب.
 */
public final class AlertSchedules {

    private AlertSchedules() {
    }

    public static AlertSchedule from(CenterSettings settings) {
        return AlertSchedule.of(settings.getAlertScanTime());
    }

    /** وصف الموعد بلغة الواجهة، لعرضه في شاشة إدارة التنبيهات */
    public static String describe(AlertSchedule schedule) {
        return I18n.format("alerts.scheduleDaily", schedule.clock());
    }
}
