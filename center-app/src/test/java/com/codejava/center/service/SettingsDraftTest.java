package com.codejava.center.service;

import com.codejava.center.TestActor;
import com.codejava.center.config.SecurityConfig;
import com.codejava.center.core.backup.BackupFrequency;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.enums.Currency;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.service.dto.CenterSettingsDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * صفُّ الإعدادات واحد، وشاشتان تكتبان فيه، ومجدوِلان.
 *
 * <p>وما دامت الكتابة تمرّ بالصفّ كاملاً فكلُّ كاتبٍ يمحو ما كتبه غيره. وهذا ما كان
 * يقع: شاشةُ الإعدادات تبني {@code CenterSettings} جديداً كاملاً وهي لا تحمل حقلَ
 * تنبيهاتٍ واحداً، فكانت كلُّ ضغطة "حفظ" فيها تُطفئ التنبيهات وتمحو تاريخ آخر فحص -
 * بلا رسالةٍ ولا أثر، والنتيجة تُقرأ بعد أسابيع على أنها "لم يصلني أي تنبيه".</p>
 */
@DataJpaTest
@Import({SettingsService.class, AuditService.class, TestActor.class, SecurityConfig.class})
class SettingsDraftTest {

    @Autowired private SettingsService settingsService;
    @Autowired private CenterSettingsRepository settingsRepository;
    @Autowired private TestEntityManager entityManager;

    /** هذا هو سبب غياب الحقلين عن المسودة */
    @Test
    void savingTheSettingsScreenDoesNotSilenceAlerts() {
        LocalDateTime lastScan = LocalDateTime.of(2026, 9, 20, 18, 30);
        seed(stored -> {
            stored.setAlertsEnabled(true);
            stored.setAlertScanTime(LocalTime.of(18, 0));
            stored.setLastAlertScanAt(lastScan);
        });

        settingsService.save(draft());

        CenterSettings after = settingsService.getSettings();
        assertThat(after.isAlertsEnabled()).isTrue();
        assertThat(after.getAlertScanTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(after.getLastAlertScanAt()).isEqualTo(lastScan);
    }

    /**
     * ومثلُه تاريخُ آخر نسخة احتياطية ناجحة.
     *
     * <p>كانت الشاشة تحمله بيدها حقلاً مؤقتاً فيها، وهي حراسةٌ تعيش في شاشة: الحافة
     * هي الشاشة التالية، ولا سطرَ لها فيه.</p>
     */
    @Test
    void savingTheSettingsScreenDoesNotEraseTheLastBackupStamp() {
        LocalDateTime lastBackup = LocalDateTime.of(2026, 9, 21, 2, 0);
        seed(stored -> stored.setLastAutoBackupAt(lastBackup));

        settingsService.save(draft());

        assertThat(settingsService.getSettings().getLastAutoBackupAt()).isEqualTo(lastBackup);
    }

    /** وما تملكه المسودة يُكتب فعلاً - وإلا كان الحفظ لا يحفظ */
    @Test
    void whatTheDraftCarriesIsWritten() {
        seed(stored -> stored.setCenterName("اسمٌ قديم"));

        settingsService.save(draft());

        CenterSettings after = settingsService.getSettings();
        assertThat(after.getCenterName()).isEqualTo("سنتر النور");
        assertThat(after.getCurrency()).isEqualTo(Currency.EGP);
        assertThat(after.getBackupFrequency()).isEqualTo(BackupFrequency.DAILY);
        assertThat(after.getLedgerStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    /** ومركزُ التنبيهات يكتب حقلَيه ولا يمسّ الباقي: الاتجاه الآخر من الباب نفسه */
    @Test
    void theAlertCentreWritesItsTwoFieldsAndLeavesTheRest() {
        seed(stored -> {
            stored.setCenterName("سنتر النور");
            stored.setBackupPath("/backups");
        });

        settingsService.saveAlertScan(true, LocalTime.of(7, 30));

        CenterSettings after = settingsService.getSettings();
        assertThat(after.isAlertsEnabled()).isTrue();
        assertThat(after.getAlertScanTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(after.getCenterName()).isEqualTo("سنتر النور");
        assertThat(after.getBackupPath()).isEqualTo("/backups");
    }

    private void seed(java.util.function.Consumer<CenterSettings> fill) {
        CenterSettings stored = CenterSettings.builder().build();
        fill.accept(stored);
        settingsRepository.saveAndFlush(stored);
        entityManager.clear();
    }

    /** ما ترسله شاشةُ الإعدادات: لا معرّف، ولا تنبيهات، ولا ختمَ مجدوِل */
    private static CenterSettingsDraft draft() {
        return new CenterSettingsDraft("سنتر النور", "01000000000", null, "/backups", true,
                Currency.EGP, BackupFrequency.DAILY, LocalTime.of(2, 0), null, null, 30,
                null, null, null, null, null, null,
                LocalDate.of(2026, 1, 1));
    }
}
