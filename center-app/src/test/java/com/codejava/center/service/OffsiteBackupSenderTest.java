package com.codejava.center.service;

import com.codejava.center.core.backup.OffsiteBackup;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * خروجُ النسخة من الجهاز: متى يُحاوَل، ومتى يُقال إنه لم يقع.
 *
 * <p>والعطبُ الذي يوجد هذا الصفُّ لأجله ليس رفعاً يفشل - ذاك يُرى - بل <b>رفعٌ يفشل
 * بصمت</b>: سنترٌ ضُبط له مستودع، وكلَّ ليلة تُكتب نسخةٌ وتبقى على القرص، والشاشةُ
 * تقول "تمت النسخة". فيبقى ظنُّ صاحبها أن نسخَه خارج الخادم إلى اليوم الذي يُطلب فيه
 * ذلك، والظنُّ هو ما يمنع السؤال.</p>
 *
 * <p>ولا شبكةَ هنا: ما يُفحص قراراتُ المُرسِل قبل الطلب وبعده، وهي حيث يقع الخطأ.
 * الرفعُ نفسه سطرٌ من مكتبة JDK.</p>
 */
class OffsiteBackupSenderTest {

    @TempDir
    Path folder;

    /** لا عنوانَ مضبوط: قرارٌ لا عطل، فلا يُقال شيء ولا يُطلق تنبيه */
    @Test
    void withNoStoreConfiguredNothingIsAttemptedAndNothingIsSaid() throws IOException {
        OffsiteCopy copy = send(destination(null, "s3cret", null));

        assertThat(copy.requested()).isFalse();
        assertThat(copy.failedAfterBeingAsked()).isFalse();
        assertThat(copy.details()).isEqualTo("offsite=off");
    }

    /**
     * <b>عنوانٌ بلا رمز ليس "غير مضبوط" بل مضبوطٌ ومكسور.</b>
     *
     * <p>أحدُهم كتب العنوان قاصداً. وقراءةُ ذلك على أنه "لم يُطلب" تُسكت العطبَ تماماً،
     * فتمرّ الليالي والنسخةُ لم تبرح الخادم ولا شيء يقول ذلك.</p>
     */
    @Test
    void aStoreUrlWithNoTokenIsAFaultNotAChoice() throws IOException {
        OffsiteCopy copy = send(destination("https://store.example.com/backups", null, null));

        assertThat(copy.requested()).isTrue();
        assertThat(copy.stored()).isFalse();
        assertThat(copy.failedAfterBeingAsked()).isTrue();
        assertThat(copy.problem()).isEqualTo(I18n.get("error.offsite.tokenMissing"));
    }

    /**
     * وعنوانٌ يشير إلى داخل الشبكة يُردّ قبل أن يخرج الرمز.
     *
     * <p>نفسُ حارس بوابة الرسائل ولنفس السبب: عنوانٌ يكتبه إنسان ويفتحه البرنامج من
     * داخل الشبكة حاملاً {@code Bearer} هو SSRF بعينه، و{@code 169.254.169.254} -
     * واجهةُ بيانات المستضيف - أولُ ما يُجرَّب.</p>
     */
    @Test
    void aStoreThatPointsInsideTheNetworkIsRefusedBeforeTheTokenLeaves() throws IOException {
        assertThat(send(destination("https://169.254.169.254/backups", "s3cret", null))
                .failedAfterBeingAsked()).isTrue();

        assertThat(send(destination("https://127.0.0.1/backups", "s3cret", null))
                .failedAfterBeingAsked()).isTrue();

        // وhttp ليس https: رمزٌ يمشي على نصٍّ مكشوف يُقرأ في الطريق
        assertThat(send(destination("http://store.example.com/backups", "s3cret", null))
                .failedAfterBeingAsked()).isTrue();
    }

    /** ولا شيء من ذلك يرمي: النسخةُ نجحت قبل أن يُستدعى الرفع، وقلبُها فشلاً كذبة */
    @Test
    void nothingHereEverThrowsBecauseTheBackupItselfAlreadySucceeded() throws IOException {
        Path missing = folder.resolve("ليست-موجودة.sql.enc");
        OffsiteCopy copy = new OffsiteBackupSender(
                destination("https://store.example.com/backups", "s3cret", null)).send(missing);

        assertThat(copy.failedAfterBeingAsked()).isTrue();
        assertThat(copy.problem()).isNotBlank();
    }

    /**
     * والمجلَّد يدخل العنوان، فلا تكتب نسخةُ سنترٍ فوق نسخة جاره.
     *
     * <p>أسماءُ الملفات ختمٌ زمني وحده، ونسختان أُخذتا في الثانية نفسها في مستودعٍ
     * واحد تكتب إحداهما فوق الأخرى - عطبٌ لا يُكتشف إلا يوم الاستعادة.</p>
     */
    @Test
    void eachCentreGetsItsOwnFolderInsideTheStore() {
        assertThat(OffsiteBackupSender.join(
                "https://store.example.com/backups", "t_cairo", "backup_2026-09-21_02-00-00.sql.enc"))
                .isEqualTo("https://store.example.com/backups/t_cairo/backup_2026-09-21_02-00-00.sql.enc");

        // وبلا مجلَّد - خادمٌ لسنترٍ واحد - يُرفع إلى الجذر
        assertThat(OffsiteBackupSender.join("https://store.example.com/backups", null, "b.enc"))
                .isEqualTo("https://store.example.com/backups/b.enc");
    }

    /** والشرطات تُنظَّف: جذرٌ ينتهي بها ومجلَّدٌ يبدأ بها يصنعان مجلَّداً فارغاً في المسار */
    @Test
    void aTrailingSlashOnTheStoreDoesNotBecomeAnEmptyFolder() {
        assertThat(OffsiteBackupSender.join(
                "https://store.example.com/backups/", "/t_cairo/", "b.enc"))
                .isEqualTo("https://store.example.com/backups/t_cairo/b.enc");
    }

    /**
     * وكلُّ 2xx نجاح لا {@code 200} وحدها.
     *
     * <p>المستودعات تردّ {@code 201} على إنشاء و{@code 204} على كتابةٍ بلا محتوى.
     * ورفضُهما يقلب رفعاً وقع فعلاً إلى تنبيهٍ كاذب كلَّ ليلة - ثم إلى مشغّلٍ تعلّم
     * ألا ينظر في التنبيه.</p>
     */
    @Test
    void everySuccessfulStatusCountsAsStoredNotJustTwoHundred() {
        URI target = URI.create("https://store.example.com/backups/t_cairo/b.enc");

        for (int status : new int[]{200, 201, 202, 204}) {
            assertThat(OffsiteBackupSender.interpret(status, "", target).stored())
                    .as("الردّ " + status)
                    .isTrue();
        }
        assertThat(OffsiteBackupSender.interpret(200, "", target).locator())
                .isEqualTo(target.toString());
    }

    /** والرفضُ يحمل رمزَه: "فشل الرفع" وحدها لا تقول لمن يقرأ أهي صلاحيةٌ أم مساحة */
    @Test
    void aRefusalCarriesTheStoresOwnCodeSoTheReasonIsActionable() {
        URI target = URI.create("https://store.example.com/backups/b.enc");

        OffsiteCopy denied = OffsiteBackupSender.interpret(403, "forbidden", target);
        assertThat(denied.failedAfterBeingAsked()).isTrue();
        assertThat(denied.problem()).contains("403");

        assertThat(OffsiteBackupSender.interpret(507, "no space", target).problem())
                .contains("507");
    }

    private OffsiteCopy send(OffsiteBackup destination) throws IOException {
        Path file = folder.resolve("backup_2026-09-21_02-00-00.sql.enc");
        Files.writeString(file, "ciphertext");
        return new OffsiteBackupSender(destination).send(file);
    }

    private static OffsiteBackup destination(String endpoint, String token, String folder) {
        return new OffsiteBackup() {
            @Override
            public String endpoint() {
                return endpoint;
            }

            @Override
            public char[] token() {
                return token == null ? null : token.toCharArray();
            }

            @Override
            public String folder() {
                return folder;
            }
        };
    }
}
