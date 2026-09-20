package com.codejava.center.config;

import com.codejava.center.core.secret.MessagingSecretStore;
import com.codejava.center.service.notification.MessagingLinkPreferences;
import com.codejava.center.service.notification.WhatsAppLinkStyle;
import com.codejava.center.util.NotificationPreferences;
import org.springframework.stereotype.Component;

/**
 * تركيب نصف الإشعارات الذي يخصّ هذا الجهاز: المفتاح السرّي وشكل الرابط.
 *
 * <p>صفّ واحد لواجهتين لأن مصدرهما واحد — {@link NotificationPreferences} — وفصلُهما
 * في صفّين يعني ملفين يقرآن نفس السجلّ. أما الواجهتان فمنفصلتان لأن الفرق بينهما حقيقي:
 * المفتاح سرٌّ تعرفه النواة بصفته سرّاً ({@link MessagingSecretStore})، وشكل الرابط
 * تفضيلُ جهازٍ بنوعٍ من طبقة الأعمال ({@link MessagingLinkPreferences}).</p>
 */
@Component
public class DesktopMessagingPreferences implements MessagingSecretStore, MessagingLinkPreferences {

    @Override
    public char[] apiToken() {
        return NotificationPreferences.apiToken();
    }

    @Override
    public WhatsAppLinkStyle linkStyle() {
        return NotificationPreferences.linkStyle();
    }

    @Override
    public String linkTemplate() {
        return NotificationPreferences.linkTemplate();
    }
}
