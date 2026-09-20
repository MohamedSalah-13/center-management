package com.codejava.center.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * أسرار الخادم من بيئة التشغيل.
 *
 * <p>ما يُفحص هنا هو الغياب لا الحضور: كلمةُ مرورٍ فارغة تُشفَّر بها النسخ تعني نسخاً
 * مفتوحة يظنّها صاحبها مغلقة - وهو عطلٌ لا يُكتشف إلا يوم تُطلب نسخة.</p>
 */
class EnvironmentSecretsTest {

    @Test
    void anAbsentPassphraseMeansNoEncryptionRatherThanAnEmptyOne() {
        EnvironmentSecrets secrets = new EnvironmentSecrets(new MockEnvironment());

        assertThat(secrets.encryptionEnabled()).isFalse();
        assertThat(secrets.passphrase()).isNull();
        assertThat(secrets.apiToken()).isNull();
    }

    /** متغيّرٌ مضبوط على مسافات ليس سرّاً */
    @Test
    void aBlankValueCountsAsAbsent() {
        EnvironmentSecrets secrets = new EnvironmentSecrets(
                new MockEnvironment().withProperty("CENTER_BACKUP_PASSPHRASE", "   "));

        assertThat(secrets.encryptionEnabled()).isFalse();
    }

    @Test
    void whatIsSetIsHandedBack() {
        EnvironmentSecrets secrets = new EnvironmentSecrets(new MockEnvironment()
                .withProperty("CENTER_BACKUP_PASSPHRASE", "a-long-passphrase")
                .withProperty("CENTER_MESSAGING_TOKEN", "TOKEN-123"));

        assertThat(secrets.encryptionEnabled()).isTrue();
        assertThat(secrets.passphrase()).containsExactly("a-long-passphrase".toCharArray());
        assertThat(secrets.apiToken()).containsExactly("TOKEN-123".toCharArray());
    }
}
