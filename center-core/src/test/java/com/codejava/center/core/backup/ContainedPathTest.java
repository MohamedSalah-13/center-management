package com.codejava.center.core.backup;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * حدُّ المسار الذي يكتبه العميل.
 *
 * <p>ما يُفحص هنا هو ما لا يُرى: مسارٌ يخرج عن جذره بـ {@code ..} يبدو صحيحاً في
 * الشاشة، ويكتب dump سنترٍ كامل حيث يقرؤه غيره.</p>
 */
class ContainedPathTest {

    private static final Path ROOT = Path.of("backups", "cairo").toAbsolutePath();

    @Test
    void aPathInsideTheRootPassesThroughNormalised() {
        assertThat(ContainedPath.resolve(ROOT, ROOT.resolve("daily/../nightly")))
                .isEqualTo(ROOT.resolve("nightly"));
    }

    @Test
    void theRootItselfIsInside() {
        assertThat(ContainedPath.isInside(ROOT, ROOT)).isTrue();
    }

    /** الشكل الذي وُجد هذا الصنف لأجله */
    @Test
    void climbingOutWithDotDotIsRefused() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, ROOT.resolve("../giza")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPathSomewhereElseEntirelyIsRefused() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, ROOT.getParent().resolveSibling("elsewhere")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ContainedPath.isInside(ROOT, ROOT.resolveSibling("giza"))).isFalse();
    }

    /**
     * جارٌ يبدأ اسمه باسم الجذر ليس داخله: {@code .../cairo2} يبدأ بـ {@code .../cairo}
     * نصّاً، ومقارنةُ نصوص وحدها تقبله.
     */
    @Test
    void aSiblingWhoseNameStartsWithTheRootIsNotInside() {
        assertThat(ContainedPath.isInside(ROOT, ROOT.resolveSibling("cairo2"))).isFalse();
    }

    /** جذرٌ غائب يعني بلا حدّ، وهو جواب سطح المكتب: قرصُ صاحبه قرصُه */
    @Test
    void withNoRootAnyPathIsAllowed() {
        assertThat(ContainedPath.resolve(null, Path.of("anywhere", "at", "all")))
                .isEqualTo(Path.of("anywhere", "at", "all").toAbsolutePath());
    }

    @Test
    void refusesNothingAtAll() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
