package com.codejava.center.service;

import com.codejava.center.util.BackupCrypto;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * كم نسخة تبقى في مجلد النسخ، وأيّ الملفات تُحذف.
 *
 * <p>صنف خالٍ من Spring ومن نظام الملفات عن قصد، تماماً كـ {@link BackupSchedule} وكـ
 * {@code Printing.pageBreaks}: هذا هو القرار الذي يحذف ملفات، وخطؤه لا يُكتشف إلا يوم
 * تُطلب نسخة لم تعد موجودة. {@code BackupRetentionTest} يغطّيه بلا قرص ولا سياق تطبيق.</p>
 *
 * <h2>لماذا الافتراضي هنا لا في الشاشة</h2>
 *
 * <p>{@code backup_retention_count} عمود يقبل NULL: {@code V2} أضافه فارغاً لكل قاعدة
 * قائمة، ولا سطر إعدادات مبدئي في المخطط أصلاً. كانت شاشة الإعدادات تعرض 30 حين تجد
 * NULL بينما تقرأ الخدمة NULL على أنه "احتفظ بالكل"، فالنتيجة العملية لسنتر رقّى البرنامج
 * ولم يضغط "حفظ الإعدادات": الشاشة تقول ثلاثين والمجلد يمتلئ إلى ما لا نهاية، وهو ما
 * يُكتشف حين يمتلئ القرص لا قبله. الافتراضي صار في مكان واحد تقرأه الخدمة والشاشة معاً -
 * نفس قاعدة {@link BackupSchedule#from} في المواعيد.</p>
 *
 * <p>الصفر يبقى "احتفظ بالكل"، وهو اختيار صريح من المستخدم لا قيمة غائبة - وهذا الفرق
 * بالضبط هو ما ضاع حين كان {@code null} والصفر يعنيان الشيء نفسه.</p>
 */
public final class BackupRetention {

    /** عدد النسخ المحتفظ بها حين لا تحمل الإعدادات قيمة، وهو ما تعرضه الشاشة */
    public static final int DEFAULT_COUNT = 30;

    /** اختيار صريح بالاحتفاظ بكل النسخ */
    public static final int KEEP_ALL = 0;

    public static final int MAX_COUNT = 999;

    static final String FILE_PREFIX = "backup_";
    static final String SQL_SUFFIX = ".sql";

    private BackupRetention() {
    }

    /**
     * حصيلة الحذف بعد نسخة ناجحة.
     *
     * <p>أرقام لا نصّ: الشاشة تبني منها جملة بلغة المستخدم، وسجل المراقبة يبني منها سطراً
     * محايد اللغة. لو كانت نصاً واحداً لوجب أن يكون بإحدى اللغتين، فيُجمَّد سطر السجل على
     * لغة الجهاز الذي كتبه - نفس قاعدة بقية السجل.</p>
     *
     * @param unreadable المجلد نفسه تعذّرت قراءته: قرص خارجي فُصل بين النسخة والحذف
     */
    public record Pruned(int deleted, int failed, boolean unreadable) {

        public static final Pruned OFF = new Pruned(0, 0, false);

        public static Pruned folderUnreadable() {
            return new Pruned(0, 0, true);
        }

        /** ما يُضاف إلى سطر سجل المراقبة، بصيغة {@code key=value} كبقيته */
        public String details() {
            if (unreadable) {
                return "pruneFailed=unreadable";
            }
            return "pruned=" + deleted + (failed > 0 ? "; pruneFailed=" + failed : "");
        }
    }

    /**
     * العدد المعمول به: الغائب يصير الافتراضي، والخارج عن المجال يُقصَر عليه.
     *
     * @param stored ما في {@code CenterSettings.backupRetentionCount}، وقد يكون {@code null}
     */
    public static int resolve(Integer stored) {
        if (stored == null) {
            return DEFAULT_COUNT;
        }
        return Math.min(Math.max(stored, KEEP_ALL), MAX_COUNT);
    }

    /**
     * أسماء الملفات التي يجب حذفها ليبقى {@code keep} منها.
     *
     * <p>الترتيب بالاسم لا بتاريخ التعديل: الاسم يحمل الطابع الزمني ولا يتغيّر بنسخ الملفات
     * من مجلد إلى آخر أو باستعادة المجلد نفسه من نسخة، بينما تاريخ التعديل يتغيّر - ونسخة
     * قديمة نُسخت اليوم تبدو حينها أحدث ما في المجلد فتنجو ويُحذف ما هو أحدث منها فعلاً.</p>
     *
     * <p>ما لم ينشئه البرنامج لا يُمسّ ولا يُعدّ: مجلد النسخ قد يكون مجلد مستندات فيه ملفات
     * لصاحب السنتر، وحذف ملف لم نكتبه نحن ليس شيئاً يُصحَّح بعد وقوعه.</p>
     */
    public static List<String> obsolete(List<String> fileNames, int keep) {
        if (keep <= KEEP_ALL) {
            return List.of();
        }
        return fileNames.stream()
                .filter(BackupRetention::isBackupFile)
                .sorted(Comparator.<String>naturalOrder().reversed())
                .skip(keep)
                .toList();
    }

    /** هل هذا ملف نسخة كتبه البرنامج - مشفَّراً كان أو صريحاً */
    public static boolean isBackupFile(String fileName) {
        return fileName.startsWith(FILE_PREFIX)
                && Stream.of(SQL_SUFFIX, SQL_SUFFIX + BackupCrypto.ENCRYPTED_SUFFIX)
                .anyMatch(fileName::endsWith);
    }
}
