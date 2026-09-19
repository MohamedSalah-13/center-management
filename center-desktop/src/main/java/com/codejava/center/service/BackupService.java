package com.codejava.center.service;

import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.BackupCrypto;
import com.codejava.center.util.BackupPreferences;
import com.codejava.center.util.I18n;
import com.codejava.center.util.MySqlLocator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * أخذ نسخة احتياطية من قاعدة البيانات واستعادتها عبر {@code mysqldump} و{@code mysql}.
 *
 * <p><b>الخدمة ترمي استثناءً برسالة مترجمة عند الفشل ولا تعيد {@code false}.</b> كانت تعيد
 * قيمة منطقية وتطبع أثر الخطأ في الطرفية، وفي نسخة jpackage لا طرفية أصلاً: كان المستخدم
 * يرى "فشلت العملية" ولا سبيل لمعرفة هل السبب مجلد محذوف أم كلمة مرور خاطئة أم
 * {@code mysqldump} غير موجود. الآن يصل نصّ خطأ الأداة نفسه إلى الشاشة عبر
 * {@link com.codejava.center.util.FxAsync}.</p>
 *
 * <p>{@code executeBackup} <b>بلا حارس صلاحيات عن قصد</b>: المهمة المجدولة تعمل بلا جلسة
 * مستخدم، وإضافة {@code @RequiresRole} إليها تعني توقّف كل النسخ التلقائية. المقيَّد هو
 * {@code restoreBackup} وحده لأنه العملية المدمِّرة.</p>
 *
 * <p>مخرجات {@code mysqldump} تُقرأ من التيار ولا تُكتب بـ {@code -r}: هكذا يمكن تمريرها
 * على التشفير قبل أن تلمس القرص، فلا توجد لحظة يكون فيها ملف صريح بكل بيانات السنتر
 * على الجهاز. راجع {@link BackupCrypto}.</p>
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** الطابع الزمني كامل في اسم الملف: باسم يحمل التاريخ وحده كانت نسخة اليوم الثانية تمحو الأولى */
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String FILE_PREFIX = BackupRetention.FILE_PREFIX;
    private static final String SQL_SUFFIX = BackupRetention.SQL_SUFFIX;

    /** حدّ أقصى يمنع عملية معلّقة من احتجاز خيط الجدولة إلى الأبد */
    private static final long TIMEOUT_MINUTES = 30;

    /** آخر ما يُعرض من رسائل الأداة عند الفشل؛ الأثر الكامل قد يكون آلاف الأسطر */
    private static final int ERROR_TAIL_CHARS = 600;

    /**
     * أرقام أخطاء MySQL التي تعني نقص صلاحية في مستخدم قاعدة البيانات:
     * 1044 (منع على القاعدة)، 1142 (منع أمر على جدول)، 1227 (صلاحية على مستوى الخادم).
     */
    private static final Pattern PRIVILEGE_ERROR = Pattern.compile("ERROR (1044|1142|1227)");

    /**
     * سجل المراقبة. النسخة المجدولة تعمل بلا جلسة مستخدم فتُنسب إلى النظام، وهو
     * المقصود: لا يصحّ نسبتها إلى آخر من سجّل دخوله على الجهاز.
     */
    private final AuditService auditService;

    // بيانات الاتصال تُقرأ من الإعدادات (متغيرات البيئة) بدلاً من كتابتها داخل الكود
    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    /**
     * مجلد أدوات MySQL حين لا تكون في {@code PATH}، مضبوطاً صراحةً.
     * مثبِّت MySQL على ويندوز لا يضيفها إلى المسار افتراضياً، وكان ذلك يعني نسخاً
     * احتياطية لا تعمل عند العميل بلا سبب ظاهر. تصريحٌ فارغ هنا لا يعني عدم البحث —
     * راجع {@link #effectiveMysqlBinDir()} و{@link MySqlLocator}.
     */
    @Value("${center.backup.mysql-bin-dir:}")
    private String mysqlBinDir;

    /**
     * يأخذ نسخة احتياطية ويعيد الملف الناتج وحصيلة حذف القديم معه.
     *
     * <p>الحصيلة جزء من الجواب لا تفصيل داخلي: "تمت النسخة" وحدها تركت المستخدم لا يعرف
     * هل عمل عدد النسخ المحفوظة أم لا، فلا سبيل أمامه إلا عدّ الملفات في المجلد - وهو
     * بالضبط ما يصعب عدّه، لأن ما نسخه بيده من الملفات لا يعدّه البرنامج.</p>
     *
     * @param retentionCount عدد النسخ المحتفظ بها في المجلد؛ {@code null} = الافتراضي،
     *                       والصفر وحده يعني الاحتفاظ بالكل. راجع {@link BackupRetention}
     * @throws IllegalStateException برسالة مترجمة عند أي فشل
     */
    public BackupOutcome executeBackup(String targetDirectory, Integer retentionCount) {
        if (targetDirectory == null || targetDirectory.isBlank()) {
            throw new IllegalStateException(I18n.get("error.backup.noDirectory"));
        }

        Path directory = Path.of(targetDirectory);
        try {
            // المجلد قد يكون على قرص خارجي غير موصول: الإنشاء يفشل هنا برسالة واضحة
            // بدل أن يفشل mysqldump برسالة عن ملف لا يستطيع فتحه
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.directoryUnusable",
                    directory, messageOf(e)), e);
        }

        boolean encrypt = BackupPreferences.encryptionEnabled();
        char[] passphrase = encrypt ? BackupPreferences.passphrase() : null;
        if (encrypt && (passphrase == null || passphrase.length == 0)) {
            throw new IllegalStateException(I18n.get("error.backup.noPassphrase"));
        }

        Path target = directory.resolve(FILE_PREFIX + LocalDateTime.now().format(FILE_STAMP)
                + SQL_SUFFIX + (encrypt ? BackupCrypto.ENCRYPTED_SUFFIX : ""));

        try {
            dump(target, passphrase);
        } catch (RuntimeException e) {
            delete(target); // ملف نصف مكتوب يبدو نسخة صالحة في قائمة المجلد
            // النسخة الفاشلة أهمّ من الناجحة في السجل: لا شيء آخر يُظهر ليلة بلا نسخة
            auditService.recordFailure(AuditAction.BACKUP_CREATED,
                    target.getFileName().toString(), messageOf(e));
            throw e;
        } finally {
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }

        int keep = BackupRetention.resolve(retentionCount);
        BackupRetention.Pruned pruned = prune(directory, keep);

        auditService.record(AuditAction.BACKUP_CREATED, null, target.getFileName().toString(),
                "encrypted=" + encrypt + "; retention=" + keep + "; " + pruned.details());
        return new BackupOutcome(target, pruned);
    }

    /**
     * يستعيد نسخة احتياطية فوق قاعدة البيانات الحالية.
     *
     * @param passphrase كلمة مرور فكّ التشفير، تُتجاهل إن كان الملف غير مشفَّر
     */
    @RequiresRole(Role.ADMIN)
    public void restoreBackup(String sqlFilePath, char[] passphrase) {
        Path source = Path.of(sqlFilePath);
        if (!Files.isReadable(source)) {
            throw new IllegalStateException(I18n.format("error.backup.fileUnreadable", source));
        }

        Path plain = source;
        Path temporary = null;
        try {
            if (BackupCrypto.isEncrypted(source)) {
                if (passphrase == null || passphrase.length == 0) {
                    throw new IllegalStateException(I18n.get("error.backup.noPassphrase"));
                }
                temporary = Files.createTempFile("center-restore-", SQL_SUFFIX);
                BackupCrypto.decrypt(source, temporary, passphrase);
                plain = temporary;
            }

            run(processBuilder(mysqlTool("mysql")).redirectInput(plain.toFile()), null, null);

            // الاستعادة تكتب فوق كل شيء، بما فيه سجل المراقبة نفسه: السطر الذي يُكتب هنا
            // هو أول ما يبقى بعدها، وبه يُعرف أن ما قبله محتوى ملف لا تاريخ السنتر.
            //
            // ويُكتب خارج قاعدة الصف "ما لا يمكن تسجيله لا يحدث": القاعدة قد استُبدلت
            // للتوّ، وفشل الكتابة عليها لا يُلغي استعادة تمّت فعلاً - إعلان نجاحها فشلاً
            // يدفع من أمام الشاشة إلى تكرارها، وهي عملية مدمّرة
            try {
                auditService.record(AuditAction.BACKUP_RESTORED, null,
                        source.getFileName().toString(), "encrypted=" + (temporary != null));
            } catch (RuntimeException e) {
                log.error("نجحت الاستعادة لكن تعذر تسجيلها في سجل المراقبة", e);
            }
        } catch (IOException e) {
            auditService.recordFailure(AuditAction.BACKUP_RESTORED,
                    source.getFileName().toString(), messageOf(e));
            throw new IllegalStateException(I18n.format("error.backup.restoreFailed", messageOf(e)), e);
        } catch (RuntimeException e) {
            auditService.recordFailure(AuditAction.BACKUP_RESTORED,
                    source.getFileName().toString(), messageOf(e));
            throw e;
        } finally {
            // الملف الوسيط نسخة صريحة كاملة من قاعدة البيانات: لا يُترك على القرص
            delete(temporary);
        }
    }

    /** ينفّذ {@code mysqldump} ويكتب مخرجاته - مشفَّرة أو صريحة - في {@code target} */
    private void dump(Path target, char[] passphrase) {
        List<String> command = new ArrayList<>(List.of(
                mysqlTool("mysqldump"),
                "--host=" + host(dbUrl),
                "--port=" + port(dbUrl),
                "--user=" + dbUsername,
                // بدونها يخرج الملف بترميز الأداة الافتراضي فتتحول أسماء الطلاب إلى علامات استفهام
                "--default-character-set=utf8mb4",
                // نسخة متسقة بلا قفل الجداول. ليست تحسيناً فقط: بدونها تلجأ الأداة إلى
                // LOCK TABLES وهي صلاحية لا يملكها مستخدم القاعدة المحدود الصلاحيات الذي
                // يصفه docs/first-install.md، فتفشل النسخة. وهي أيضاً ما يسمح بأخذ نسخة
                // يدوية والسنتر مفتوح والحضور يُسجَّل.
                "--single-transaction",
                // المخطط تديره Flyway ولا يحوي triggers ولا routines ولا events. وقراءتها
                // تتطلب صلاحيات TRIGGER و EVENT إضافية، فطلبها يعني فشل كل النسخ عند من
                // ركّب النظام بالصلاحيات الموصى بها - مقابل نسخ كائنات لا وجود لها.
                "--skip-triggers",
                // بدونها يكتب mysqldump في أول الملف - حين يكون GTID مفعَّلاً على الخادم،
                // وهو حال أغلب صور Docker - سطرَي SET @@SESSION.SQL_LOG_BIN و
                // SET @@GLOBAL.GTID_PURGED. تنفيذهما يتطلب SUPER، فتتوقف الاستعادة عند
                // أول سطر بالخطأ 1227 وقاعدة البيانات لم تُلمس بعد. وهما بيانات نسخ
                // تماثلي لا معنى لها لسنتر بخادم واحد.
                "--set-gtid-purged=OFF",
                // قراءة معلومات مساحات الجداول تتطلب صلاحية PROCESS وهي على مستوى الخادم
                // كله لا على قاعدة السنتر، ولا يملكها المستخدم المحدود الصلاحيات
                "--no-tablespaces",
                // --add-locks (المفعَّلة ضمن --opt افتراضياً) تكتب LOCK TABLES حول صفوف كل
                // جدول لتسريع الإدخال. وهي غير --single-transaction: تلك تخصّ لحظة أخذ
                // النسخة، وهذه أوامر تُكتب داخل الملف وتُنفَّذ وقت الاستعادة - فتحتاج صلاحية
                // LOCK TABLES التي لا يملكها المستخدم، وتتوقف الاستعادة بالخطأ 1044 عند
                // أول جدول. التسريع لا يعني شيئاً بحجم قاعدة سنتر.
                "--skip-add-locks",
                dbName(dbUrl)));

        run(processBuilder(command), target, passphrase);
    }

    /**
     * يشغّل العملية، ويكتب مخرجاتها في {@code target} إن طُلب ذلك.
     *
     * <p>تيار الخطأ يُقرأ على خيط مستقل: أنبوب النظام محدود السعة، وعملية تكتب فيه أكثر
     * منها بينما نحن ننتظر انتهاءها تتوقف هي وننتظر نحن إلى الأبد.</p>
     */
    private void run(ProcessBuilder processBuilder, Path target, char[] passphrase) {
        if (target == null) {
            // مخرجات mysql عند الاستعادة لا تهمّنا، لكن أنبوباً لا يقرؤه أحد يملأ فتتوقف العملية
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.toolMissing",
                    processBuilder.command().get(0), messageOf(e)), e);
        }

        try {
            // StringBuffer لا StringBuilder: يكتب فيه خيط القراءة ونقرؤه نحن، وقد ننتهي
            // من الانتظار قبل أن ينتهي هو
            StringBuffer errors = new StringBuffer();
            Thread drain = drainErrors(process, errors);

            if (target != null) {
                try (InputStream output = process.getInputStream()) {
                    if (passphrase != null) {
                        BackupCrypto.encrypt(output, target, passphrase);
                    } else {
                        Files.copy(output, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException(I18n.format("error.backup.timedOut", TIMEOUT_MINUTES));
            }
            drain.join(TimeUnit.SECONDS.toMillis(5));

            if (process.exitValue() != 0) {
                throw new IllegalStateException(I18n.format("error.backup.toolFailed",
                        process.exitValue(), tail(errors)) + privilegeHint(errors));
            }
            if (target != null && Files.size(target) == 0) {
                // خروج بصفر وملف فارغ: يحدث حين تنجح الأداة ولا تجد جداول تقرؤها
                throw new IllegalStateException(I18n.get("error.backup.emptyOutput"));
            }
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.writeFailed", messageOf(e)), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18n.get("error.backup.interrupted"), e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Thread drainErrors(Process process, StringBuffer errors) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errors.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                // انتهت العملية وأُغلق الأنبوب؛ ما جُمع حتى الآن يكفي للتشخيص
            }
        }, "backup-stderr");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * كلمة المرور تُمرَّر عبر متغير البيئة {@code MYSQL_PWD} لا في سطر الأوامر،
     * حتى لا تظهر في قائمة العمليات لأي مستخدم على الجهاز.
     */
    private ProcessBuilder processBuilder(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (dbPassword != null && !dbPassword.isEmpty()) {
            processBuilder.environment().put("MYSQL_PWD", dbPassword);
        }
        return processBuilder;
    }

    private ProcessBuilder processBuilder(String tool) {
        return processBuilder(List.of(tool,
                "--host=" + host(dbUrl),
                "--port=" + port(dbUrl),
                "--user=" + dbUsername,
                "--default-character-set=utf8mb4",
                dbName(dbUrl)));
    }

    private String mysqlTool(String name) {
        String binDir = effectiveMysqlBinDir();
        return binDir == null || binDir.isBlank()
                ? name
                : Path.of(binDir, name).toString();
    }

    /**
     * {@code CENTER_BACKUP_MYSQL_BIN_DIR} صريح دائماً له الأولوية؛ فقط حين يكون فارغاً
     * يُستشار {@link MySqlLocator}، الذي يعيد {@code null} لو كانت الأدوات في {@code PATH}
     * أصلاً أو تعذّر إيجادها بالفحص التلقائي.
     */
    private String effectiveMysqlBinDir() {
        if (mysqlBinDir != null && !mysqlBinDir.isBlank()) {
            return mysqlBinDir;
        }
        return MySqlLocator.resolve();
    }

    /**
     * يحذف أقدم النسخ ويُبقي {@code keep} منها. القرار في {@link BackupRetention}؛ هنا
     * قراءة المجلد والحذف وحدهما.
     *
     * <p><b>لا يرمي.</b> نسخة نجحت للتوّ لا تُعلَن فاشلة لأن ملفاً قديماً في المجلد لم يُحذف -
     * والملف المحفوظ هو الغرض، والمساحة مسألة تُعالَج غداً. لكنه <b>لا يصمت</b> أيضاً: كان
     * الحذف يبتلع كل خطأ فيجد صاحب السنتر مجلداً يمتلئ بلا شيء في أي مكان يقول لماذا.
     * ملف مفتوح في برنامج آخر، أو مجلد على قرص شبكة صار للقراءة فقط، يظهر الآن في السجل
     * وفي سطر النسخة في سجل المراقبة.</p>
     *
     * <p>{@code package-private} لا {@code private}: هذه هي الخطوة التي يشتكي غيابها صاحب
     * السنتر، و{@code BackupRetention} يغطّي القرار وحده - أما أن الحذف يقع فعلاً على قرص
     * حقيقي فلا يثبته إلا اختبار يكتب ملفات ويقرأ المجلد بعده. نفس سبب كون
     * {@code dbName}/{@code host}/{@code port} أدناه package-private.</p>
     *
     * @return حصيلة الحذف، لسجل المراقبة وللشاشة معاً
     */
    BackupRetention.Pruned prune(Path directory, int keep) {
        if (keep <= BackupRetention.KEEP_ALL) {
            return BackupRetention.Pruned.OFF;
        }

        List<String> obsolete;
        try (Stream<Path> files = Files.list(directory)) {
            obsolete = BackupRetention.obsolete(
                    files.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .toList(),
                    keep);
        } catch (IOException e) {
            log.warn("تعذّرت قراءة مجلد النسخ الاحتياطية للحذف: {} - {}", directory, messageOf(e));
            // نصّ الخطأ في السجل لا في الحصيلة: سطر سجل المراقبة key=value محايد اللغة،
            // ورسالة النظام مترجَمة بلغة الجهاز الذي فشل فتُجمّد السطر عليها
            return BackupRetention.Pruned.folderUnreadable();
        }

        int deleted = 0;
        int failed = 0;
        for (String name : obsolete) {
            if (delete(directory.resolve(name))) {
                deleted++;
            } else {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("لم تُحذف {} نسخة قديمة من {}؛ قد تكون مفتوحة في برنامج آخر أو المجلد للقراءة فقط",
                    failed, directory);
        }
        return new BackupRetention.Pruned(deleted, failed, false);
    }

    /** يحذف ويقول هل نجح؛ الفشل ليس استثناءً هنا لأنه يقع دائماً بعد عملية تمّت */
    private static boolean delete(Path path) {
        if (path == null) {
            return false;
        }
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            // الملف مفتوح في برنامج آخر أو المجلد للقراءة فقط. من ينادينا هو من يقرّر
            // هل يُعلن ذلك - راجع prune - لأن السياق وحده يحدّد إن كان الفشل يعني شيئاً
            log.warn("تعذّر حذف الملف {}: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * تلميح مكتوب بلغة الواجهة حين يكون سبب الفشل نقص صلاحية لا خطأً في الملف.
     *
     * <p>رسالة {@code mysql} تقول "DROP command denied" ولا تقول ماذا يفعل صاحب السنتر.
     * التمييز بالرقم لا بنصّ الرسالة: الأرقام ثابتة مهما كانت لغة الخادم أو نسخته.</p>
     */
    private String privilegeHint(StringBuffer errors) {
        return PRIVILEGE_ERROR.matcher(errors).find()
                ? System.lineSeparator() + System.lineSeparator() + I18n.get("error.backup.privilegeHint")
                : "";
    }

    private String tail(StringBuffer errors) {
        String text = errors.toString().trim();
        if (text.isEmpty()) {
            return I18n.get("error.backup.noToolOutput");
        }
        return text.length() <= ERROR_TAIL_CHARS ? text
                : "…" + text.substring(text.length() - ERROR_TAIL_CHARS);
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ------------------------------------------------------- تحليل رابط JDBC
    // الدوال الثلاث package-private لا private: هي ما يحدّد على أي قاعدة بيانات تعمل
    // النسخة، وخطؤها يعني نسخة سليمة الشكل من قاعدة أخرى. يغطّيها JdbcUrlParsingTest.

    /**
     * اسم قاعدة البيانات من رابط JDBC.
     * مثال: {@code jdbc:mysql://localhost:3306/center_db?useSSL=false} ← {@code center_db}
     */
    String dbName(String jdbcUrl) {
        String withoutParams = beforeParams(jdbcUrl);
        int lastSlash = withoutParams.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == withoutParams.length() - 1) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        return withoutParams.substring(lastSlash + 1);
    }

    /**
     * المضيف من الرابط.
     *
     * <p>كانت الأداة تُستدعى بلا {@code --host} ولا {@code --port} فتذهب إلى
     * {@code localhost:3306} دائماً. السنتر الذي يشغّل MySQL في Docker على منفذ آخر، أو
     * على جهاز الخادم في الشبكة، كان يأخذ نسخة من قاعدة أخرى - أو يفشل - بينما البرنامج
     * نفسه يعمل على القاعدة الصحيحة. راجع docs/first-install.md §7.</p>
     */
    String host(String jdbcUrl) {
        return authority(jdbcUrl)[0];
    }

    String port(String jdbcUrl) {
        String[] parts = authority(jdbcUrl);
        return parts.length > 1 ? parts[1] : "3306";
    }

    private String[] authority(String jdbcUrl) {
        String withoutParams = beforeParams(jdbcUrl);
        int start = withoutParams.indexOf("//");
        if (start < 0) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        String rest = withoutParams.substring(start + 2);
        int slash = rest.indexOf('/');
        String hostAndPort = slash < 0 ? rest : rest.substring(0, slash);
        return hostAndPort.isBlank() ? new String[]{"localhost"} : hostAndPort.split(":", 2);
    }

    private String beforeParams(String jdbcUrl) {
        return jdbcUrl.split("\\?", 2)[0];
    }
}
