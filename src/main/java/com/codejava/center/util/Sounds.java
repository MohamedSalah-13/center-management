package com.codejava.center.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * نغمة قصيرة تُرافق البطاقة المنبثقة، فلا تمرّ في زاوية الشاشة دون أن يراها أحد.
 *
 * <p>البطاقة وحدها تكفي من ينظر إلى الشاشة، وهو بالضبط ما لا يفعله موظف الاستقبال:
 * عينه على الطالب الذي أمامه وعلى قارئ الباركود. النغمة هي ما يجعله يرفع بصره.</p>
 *
 * <h2>مولَّدة لا ملفَّ صوت</h2>
 *
 * <p>المشروع لا يشحن أي أصل ثنائي - ولذلك تُرسم أيقونة شريط المهام في الكود - ولا يعتمد
 * على {@code javafx-media}، فـ {@code AudioClip} غير موجود أصلاً. النغمتان تُحسبان هنا
 * موجةً جيبية ({@code javax.sound.sampled} من الـ JDK): لا ملف يُنسى عند التحزيم، ولا
 * ترخيص صوت يُراجَع، ولا مسار يختلف بين التشغيل من المصدر والتشغيل من jpackage.</p>
 *
 * <p>ونغمتان صاعدتان لا صفير النظام: {@code Toolkit.beep()} على ويندوز هو صوت الخطأ
 * الافتراضي - وهو ما تستعمله شاشة الحضور عمداً لأن ما يُرفض عندها خطأ فعلاً - بينما هذه
 * أخبارٌ لا عطل. وهو مع ذلك الملاذ الأخير هنا: جهازٌ بلا بطاقة صوت أو بمخرج مشغول لا
 * يجوز أن يُسكت الإشعار تماماً.</p>
 *
 * <h2>على خيط خاصّ به</h2>
 *
 * <p>{@code line.write} يحجز الخيط طوال مدة الصوت (ربع ثانية)، وتشغيله من خيط الواجهة
 * يُجمّد النافذة تلك المدة عند كل إشعار. والخيط {@code daemon} وواحد لا أكثر: خيط غير
 * daemon يمنع إغلاق البرنامج، وخيطان يتنازعان على مخرج الصوت فيفشل أحدهما.</p>
 */
public final class Sounds {

    private static final float SAMPLE_RATE = 44_100f;

    /** 16 بت، أحادي، صحيح مُوقَّع، البايت الأدنى أولاً - أبسط صيغة تقبلها كل الأجهزة */
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    /** أقلّ من الثلث: إشعارٌ يعلو على صوت السنتر يُطفَأ في أول يوم */
    private static final double AMPLITUDE = 0.28 * Short.MAX_VALUE;

    /** نغمتان صاعدتان (صول ثم دو): الصعود يُقرأ "جديد"، والهبوط يُقرأ "انتهى شيء" */
    private static final double FIRST_NOTE_HZ = 784;
    private static final double SECOND_NOTE_HZ = 1047;

    /** تُحسب مرة واحدة عند أول إشعار: نصف مليون عملية جيب لا تُعاد في كل مرة */
    private static byte[] chime;

    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "notification-sound");
        thread.setDaemon(true);
        return thread;
    });

    private Sounds() {
    }

    /**
     * يشغّل نغمة الإشعار إن كانت مفعّلة على هذا الجهاز.
     *
     * <p>يعود فوراً: التشغيل يقع على خيط آخر. ولا يرمي شيئاً مهما كان حال الصوت في
     * الجهاز - إشعارٌ بلا نغمة أهون من نافذة خطأ عن بطاقة صوت.</p>
     */
    public static void notifyUser() {
        if (!AlertPreferences.soundEnabled()) {
            return;
        }
        PLAYER.execute(Sounds::play);
    }

    private static void play() {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            byte[] samples = samples();
            line.open(FORMAT);
            line.start();
            line.write(samples, 0, samples.length);
            line.drain();
        } catch (Exception e) {
            fallbackBeep();
        }
    }

    /** لا بطاقة صوت، أو مخرج يحتكره برنامج آخر: صفير النظام خيرٌ من صمت */
    private static void fallbackBeep() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
            // جهاز بلا صوت أصلاً: البطاقة على الشاشة هي الإشعار، والنغمة كانت زيادة
        }
    }

    private static synchronized byte[] samples() {
        if (chime == null) {
            byte[] first = tone(FIRST_NOTE_HZ, 90);
            byte[] gap = new byte[frames(25) * 2];
            byte[] second = tone(SECOND_NOTE_HZ, 190);

            chime = new byte[first.length + gap.length + second.length];
            System.arraycopy(first, 0, chime, 0, first.length);
            System.arraycopy(gap, 0, chime, first.length, gap.length);
            System.arraycopy(second, 0, chime, first.length + gap.length, second.length);
        }
        return chime;
    }

    /**
     * موجة جيبية بمغلّف صعود وهبوط.
     *
     * <p>المغلّف ليس تجميلاً: موجةٌ تبدأ وتنتهي فجأة تقفز من الصفر إلى ذروتها في عيّنة
     * واحدة، والسمّاعة تُخرج ذلك طقطقةً مسموعة قبل النغمة وبعدها.</p>
     */
    private static byte[] tone(double hertz, int millis) {
        int frames = frames(millis);
        int attack = Math.max(1, frames(8));
        int release = Math.max(1, frames(50));

        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double envelope = Math.min(1.0, Math.min(i / (double) attack, (frames - i) / (double) release));
            short value = (short) (Math.sin(2 * Math.PI * hertz * i / SAMPLE_RATE) * envelope * AMPLITUDE);

            data[2 * i] = (byte) (value & 0xff);
            data[2 * i + 1] = (byte) ((value >> 8) & 0xff);
        }
        return data;
    }

    private static int frames(int millis) {
        return (int) (SAMPLE_RATE * millis / 1000.0);
    }
}
