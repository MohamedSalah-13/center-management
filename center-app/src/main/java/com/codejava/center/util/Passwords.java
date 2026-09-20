package com.codejava.center.util;

import com.codejava.center.core.security.PasswordPolicy;

/**
 * يرفض كلمة المرور المخالفة برسالة يفهمها من كتبها.
 *
 * <p>القاعدة نفسها في {@link PasswordPolicy} بالنواة: هي لا تتغيّر بلغة من يقرؤها،
 * فتقرّر وتعيد ما انكسر. والصياغة هنا حيث حزمة النصوص - نفس الفصل في كل ما نُقل إلى
 * النواة: القرار هناك، والكلام حيث تُقرأ اللغة.</p>
 */
public final class Passwords {

    private Passwords() {
    }

    /** يفترض أن الحقل مطلوب وقد تحقق منه المستدعي؛ ويتحقق هنا من حدود الخوارزمية */
    public static void validate(String password) {
        PasswordPolicy.check(password).ifPresent(violation -> {
            throw new IllegalArgumentException(switch (violation) {
                case TOO_SHORT -> I18n.format("error.password.tooShort", PasswordPolicy.MIN_LENGTH);
                case TOO_LONG -> I18n.get("error.password.tooLong");
                case MISMATCH -> I18n.get("error.password.mismatch");
            });
        });
    }

    public static void requireConfirmation(String password, String confirmation) {
        PasswordPolicy.checkConfirmation(password, confirmation).ifPresent(violation -> {
            throw new IllegalArgumentException(I18n.get("error.password.mismatch"));
        });
    }
}
