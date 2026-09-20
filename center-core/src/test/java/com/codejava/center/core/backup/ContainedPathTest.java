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

    private static final Path ROOT = Path.of("/srv/center/backups/cairo");

    @Test
    void aPathInsideTheRootPassesThroughNormalised() {
        assertThat(ContainedPath.resolve(ROOT, Path.of("/srv/center/backups/cairo/nightly")))
                .isEqualTo(Path.of("/srv/center/backups/cairo/nightly"));
    }

    @Test
    void theRootItselfIsInside() {
        assertThat(ContainedPath.isInside(ROOT, ROOT)).isTrue();
    }

    /** الشكل الذي وُجد هذا الصنف لأجله */
    @Test
    void climbingOutWithDotDotIsRefused() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, Path.of("/srv/center/backups/cairo/../giza")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPathSomewhereElseEntirelyIsRefused() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, Path.of("/etc")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ContainedPath.isInside(ROOT, Path.of("/srv/center/backups/giza"))).isFalse();
    }

    /**
     * جارٌ يبدأ اسمه باسم الجذر ليس داخله: {@code .../cairo2} يبدأ بـ {@code .../cairo}
     * نصّاً، ومقارنةُ نصوص وحدها تقبله.
     */
    @Test
    void aSiblingWhoseNameStartsWithTheRootIsNotInside() {
        assertThat(ContainedPath.isInside(ROOT, Path.of("/srv/center/backups/cairo2"))).isFalse();
    }

    /** جذرٌ غائب يعني بلا حدّ، وهو جواب سطح المكتب: قرصُ صاحبه قرصُه */
    @Test
    void withNoRootAnyPathIsAllowed() {
        assertThat(ContainedPath.resolve(null, Path.of("/anywhere/at/all")))
                .isEqualTo(Path.of("/anywhere/at/all"));
    }

    @Test
    void refusesNothingAtAll() {
        assertThatThrownBy(() -> ContainedPath.resolve(ROOT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
