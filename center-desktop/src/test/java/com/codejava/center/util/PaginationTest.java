package com.codejava.center.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * حارس توزيع الصفحات.
 *
 * <p>هذا المنطق يفشل بصمت لا بخطأ: {@code printPage} كان يطبع ما يسع الصفحة الأولى ويُسقط
 * الباقي، فتقرير المتأخرات يخرج منه أوّله ولا يشكو أحد لأن الورقة تبدو سليمة. الاختبار
 * يحرس الثابت الذي لا يجوز كسره: <b>مجموع القطع الخارجة = مجموع القطع الداخلة</b>.</p>
 *
 * <p>يُختبر {@code pageBreaks} وحده لا {@code paginate}: الثاني يحتاج مشهد JavaFX وطابعة،
 * والأول هو موضع القرار كله.</p>
 */
class PaginationTest {

    private static final double HEADER = 100;
    private static final double FOOTER = 20;
    private static final double BUDGET = 800;

    @Test
    void keepsEverythingOnOnePageWhenItFits() {
        List<Double> blocks = List.of(50.0, 50.0, 50.0);

        assertThat(Printing.pageBreaks(blocks, HEADER, FOOTER, BUDGET)).containsExactly(3);
    }

    @Test
    void splitsWhenTheContentOutgrowsThePage() {
        // 30 قطعة × 50 = 1500، والمتاح بعد الترويسة والذيل أقل من ذلك بكثير
        List<Double> blocks = Collections.nCopies(30, 50.0);

        List<Integer> perPage = Printing.pageBreaks(blocks, HEADER, FOOTER, BUDGET);

        assertThat(perPage).hasSizeGreaterThan(1);
        assertThat(perPage).allSatisfy(count -> assertThat(count).isPositive());
    }

    /** الثابت الأهم: لا قطعة تضيع ولا تتكرر مهما كان التوزيع */
    @Test
    void neverLosesOrDuplicatesABlock() {
        List<Double> blocks = Collections.nCopies(137, 43.0);

        List<Integer> perPage = Printing.pageBreaks(blocks, HEADER, FOOTER, BUDGET);

        assertThat(perPage.stream().mapToInt(Integer::intValue).sum()).isEqualTo(blocks.size());
    }

    /** الترويسة تتكرر في كل صفحة، فكل صفحة تالية تبدأ وقد استُهلك جزء من ارتفاعها */
    @Test
    void countsTheRepeatedHeaderAgainstEveryPage() {
        List<Double> blocks = Collections.nCopies(20, 60.0);

        List<Integer> withHeader = Printing.pageBreaks(blocks, HEADER, FOOTER, BUDGET);
        List<Integer> withoutHeader = Printing.pageBreaks(blocks, 0, FOOTER, BUDGET);

        assertThat(withHeader.get(0)).isLessThan(withoutHeader.get(0));
    }

    /**
     * قطعة أطول من الورقة كلها لا يمكن تقسيمها - تُترك على صفحتها ويُقصّ ما زاد.
     * الخطأ المحتمل هنا حلقة لا تنتهي أو صفحات فارغة بلا حد.
     */
    @Test
    void placesAnOversizedBlockOnItsOwnPageInsteadOfLooping() {
        List<Double> blocks = List.of(50.0, 5000.0, 50.0);

        List<Integer> perPage = Printing.pageBreaks(blocks, HEADER, FOOTER, BUDGET);

        assertThat(perPage.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(perPage).doesNotContain(0);
    }

    /** مستند بلا قطع (تقرير فارغ) يخرج بصفحة واحدة تحمل ترويسته لا بصفر صفحات */
    @Test
    void stillProducesOnePageForAnEmptyDocument() {
        assertThat(Printing.pageBreaks(List.of(), HEADER, FOOTER, BUDGET)).containsExactly(0);
    }

    /**
     * الملاءمة تتم برصف المحتوى على عرض الورقة لا بتصغيره، فالمعامل يبقى 1 ما دام المحتوى
     * داخلاً. تصغير محتوى يسع الورقة هو بالضبط ما كان يُخرج الإيصال الحراري ضئيلاً.
     */
    @Test
    void doesNotShrinkContentThatAlreadyFits() {
        // رول 80 مم ≈ 227 نقطة، وبعد الهامش الداخلي يبقى نحو 210
        assertThat(Printing.fitScale(180, 210)).isEqualTo(1);
        assertThat(Printing.fitScale(210, 210)).isEqualTo(1);
    }

    /** ولا يكبّر أبداً: مستند أضيق من الورقة يبقى بحجمه */
    @Test
    void neverEnlargesNarrowContent() {
        assertThat(Printing.fitScale(50, 560)).isEqualTo(1);
    }

    /** ما يستعصي على الالتفاف - كلمة واحدة أطول من الورقة - يُصغَّر بقدر الفائض وحده */
    @Test
    void shrinksOnlyWhatOverflows() {
        assertThat(Printing.fitScale(420, 210)).isEqualTo(0.5);
    }
}
