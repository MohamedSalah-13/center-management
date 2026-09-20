package com.codejava.center.service.notification;

import com.codejava.center.core.secret.MessagingSecretStore;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.NotificationChannel;
import com.codejava.center.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * يجمع ضبط الإرسال من مصدريه: إعدادات السنتر في قاعدة البيانات، وتفضيلات هذا الجهاز.
 *
 * <p>لا يُخزَّن الناتج: تغيير القناة من شاشة الإعدادات يجب أن يسري على الإرسال التالي
 * بلا إعادة تشغيل — وهو بالضبط ما لم تكن تسمح به الخاصية القديمة
 * {@code center.notifications.channel} التي تُقرأ مرة عند الإقلاع.</p>
 *
 * <p>ونصف الجهاز يصل عبر واجهتين لا بقراءة {@code java.util.prefs} هنا: سجلّ ويندوز
 * ليس مصدراً يملكه كل طرف يرسل رسالة. راجع {@link MessagingSecretStore} و
 * {@link MessagingLinkPreferences}.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationConfigProvider {

    private final SettingsService settingsService;

    private final MessagingSecretStore messagingSecrets;

    private final MessagingLinkPreferences linkPreferences;

    public NotificationConfig current() {
        CenterSettings settings = settingsService.getSettings();

        // قاعدة بيانات مُرقّاة من إصدار أقدم لا تحمل قناة: الرابط هو ما كان يفعله البرنامج
        NotificationChannel channel = settings.getNotificationChannel() == null
                ? NotificationChannel.WHATSAPP_LINK
                : settings.getNotificationChannel();

        return new NotificationConfig(
                channel,
                linkPreferences.linkStyle(),
                linkPreferences.linkTemplate(),
                settings.getNotificationApiUrl(),
                settings.getNotificationSenderId(),
                settings.getNotificationTemplateName(),
                settings.getNotificationTemplateLanguage(),
                settings.getNotificationBodyTemplate(),
                readToken());
    }

    /**
     * المفتاح يصير {@code String} هنا لأنه ينتهي داخل ترويسة HTTP نصّية على أي حال؛
     * المصفوفة تُمحى فوراً حتى لا تبقى نسخة ثانية منه في الذاكرة بلا داعٍ.
     */
    private String readToken() {
        char[] token = messagingSecrets.apiToken();
        if (token == null) {
            return null;
        }
        try {
            return new String(token);
        } finally {
            Arrays.fill(token, '\0');
        }
    }
}
