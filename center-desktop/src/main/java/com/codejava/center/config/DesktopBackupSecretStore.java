package com.codejava.center.config;

import com.codejava.center.core.secret.BackupSecretStore;
import com.codejava.center.util.BackupPreferences;
import org.springframework.stereotype.Component;

/**
 * تركيب {@link BackupSecretStore} على هذا الطرف: تفضيلات الجهاز
 * ({@link java.util.prefs.Preferences}) معمّاةً بـ {@code MachineSecret}.
 *
 * <p>الصفّ رقيق عن قصد: المنطق كله — التعمية، والمفاتيح، ومعنى "لم تُضبط" — يبقى في
 * {@link BackupPreferences} حيث تقرأه شاشة الإعدادات أيضاً. الجديد هنا هو الاتجاه
 * فحسب: {@code BackupService} صار يسأل واجهةً بدل أن يستورد صنفاً من {@code util}
 * لا وجود له على خادم بلا سجلّ ويندوز.</p>
 */
@Component
public class DesktopBackupSecretStore implements BackupSecretStore {

    @Override
    public boolean encryptionEnabled() {
        return BackupPreferences.encryptionEnabled();
    }

    @Override
    public char[] passphrase() {
        return BackupPreferences.passphrase();
    }
}
