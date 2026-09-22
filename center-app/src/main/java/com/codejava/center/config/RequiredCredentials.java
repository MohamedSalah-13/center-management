package com.codejava.center.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * متغيّرٌ مطلوب ولم يُضبط: يُسمَّى، ولا يُترك ليظهر رسالةَ عطلٍ أخرى.
 *
 * <h2>ما كان يقع قبل هذا الصنف</h2>
 *
 * <p>{@code spring.datasource.password=${DB_PASSWORD}} مكتوبٌ بلا قيمةٍ افتراضية عن قصد،
 * والنيّةُ المعلنة أن يسقط الإقلاعُ برسالةٍ تسمّي المتغيّر بدل أن يتصل بكلمةٍ فارغة.
 * <b>وتلك النيّة لم تكن تتحقّق.</b> مُحلِّلُ العناصر النائبة في Spring - الذي يمرّ به
 * الربطُ إلى {@code DataSourceProperties} - يتجاوز ما لا يستطيع حلَّه ويترك النصَّ
 * كما هو، فتذهب إلى MySQL كلمةُ مرورٍ نصُّها حرفياً {@code ${DB_PASSWORD}}.</p>
 *
 * <p>وما يقرؤه من ينشر عندئذ هو:</p>
 *
 * <pre>Access denied for user 'center_app'@'...' (using password: YES)</pre>
 *
 * <p>وهي جملةٌ تقول شيئاً واحداً بوضوح: <b>الكلمةُ خاطئة</b>. فيذهب صاحبُها يفحص
 * الصلاحيات ويعيد ضبط كلمة المرور في MySQL ويجرّب النطاقات، والعطلُ سطرٌ ناقص في ملف
 * الخدمة. ورسالةٌ تقود إلى المكان الخطأ أسوأ من رسالةٍ غامضة: الغامضةُ تُبقي الباحثَ
 * يبحث، وهذه تُقنعه أنه وجد.</p>
 *
 * <h2>ولماذا هنا لا في كل برنامج</h2>
 *
 * <p>{@code center-desktop} و{@code center-web} يكتبان السطرَ نفسه في ملفَّيهما، فالعطبُ
 * واحد. والفحصُ في {@code center-app} - الوحدة التي يستهلكانها معاً - يُصلحه مرةً
 * للاثنين؛ وهو لا يعرف شاشةً ولا منفذاً، فلا يخصّ حافةً دون أخرى.</p>
 *
 * <h2>ولماذا {@code EnvironmentPostProcessor}</h2>
 *
 * <p>لأنه يعمل <b>قبل أن يُبنى السياق</b>، أي قبل أن يفتح Flyway اتصالاً أو يُنشأ مجمّعٌ.
 * فحصٌ في حبّةٍ عادية يصل بعد أن يكون العطلُ قد وقع ورسالتُه المضلِّلة قد طُبعت.</p>
 *
 * <h2>وما لا يفعله</h2>
 *
 * <p>لا يطلب وجودَ المفتاح أصلاً: ملفٌّ لا يذكر {@code spring.datasource.password} يمرّ،
 * وقيمةٌ فارغة تمرّ - قاعدةٌ بلا كلمةِ مرور اختيارٌ قائم، وهو حالُ H2 في الاختبارات.
 * الشرطُ الوحيد: <b>ألّا يبقى عنصرٌ نائب بلا حلّ</b>. فما يُفحص هو ما كُتب قاصداً ولم
 * يصل إليه جواب.</p>
 */
public class RequiredCredentials implements EnvironmentPostProcessor {

    /**
     * ما يُفحص: بيانات الاتصال الثلاثة.
     *
     * <p>واحدٌ منها فقط يقع اليوم - الاثنان الآخران يحملان قيماً افتراضية - لكن الفحص
     * على الثلاثة لأن حذفَ قيمةٍ افتراضية غداً سطرٌ واحد، ولا ينبغي أن يُعيد العطبَ
     * نفسه بصمت.</p>
     */
    static final String[] CHECKED = {
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        for (String key : CHECKED) {
            String declared = declaredValueOf(environment, key);
            if (declared == null) {
                continue;
            }
            // resolvePlaceholders لا يرمي: يترك ما عجز عنه نصّاً كما هو - وذلك بالضبط
            // ما نبحث عنه، لا ما نتجنّبه
            String missing = unresolvedVariable(environment.resolvePlaceholders(declared));
            if (missing != null) {
                throw new IllegalStateException(problem(missing, key));
            }
        }
    }

    /**
     * اسمُ المتغيّر الذي بقي بلا حلّ، أو {@code null} إن كان النصّ تامّاً.
     *
     * <p>دالةٌ خالصة، وهي موضعُ الاختبار: القرارُ كلُّه هنا، وما حولها ربطٌ بـSpring.</p>
     */
    static String unresolvedVariable(String resolved) {
        if (resolved == null) {
            return null;
        }
        int start = resolved.indexOf("${");
        if (start < 0) {
            return null;
        }
        int end = resolved.indexOf('}', start);
        if (end < 0) {
            return null;
        }
        String name = resolved.substring(start + 2, end).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * القيمةُ كما كُتبت، بعناصرها النائبة.
     *
     * <p>{@code environment.getProperty} يحلّها أو يرمي، وكلاهما يضيع ما نريد قراءته.
     * فتُقرأ من مصادر الخصائص مباشرةً.</p>
     */
    private static String declaredValueOf(ConfigurableEnvironment environment, String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * الرسالة - بالإنجليزية، كرسالة {@code center.tenancy.platform.url}.
     *
     * <p>هذه تُقرأ في سجلّ إقلاعٍ عند من ينشر، قبل أن تُثبَّت لغةٌ أصلاً: {@code I18n}
     * يسأل {@code LocaleProvider} وهو لم يُركَّب بعدُ في هذه اللحظة. ورسائلُ الإعداد
     * في هذا المستودع إنجليزيةٌ في الكود لذلك، بخلاف كل ما يراه مستخدمُ السنتر.</p>
     */
    private static String problem(String variable, String key) {
        return variable + " is not set, and " + key + " needs it. "
                + "Set it in the environment before starting. "
                + "Without this check the program would carry on and fail against the database "
                + "with an access-denied message that reads like a wrong password.";
    }
}
