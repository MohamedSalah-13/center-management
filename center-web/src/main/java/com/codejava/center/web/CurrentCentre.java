package com.codejava.center.web;

import com.codejava.center.core.tenant.TenantId;

/**
 * المؤسسة على هذا الخيط، أو {@code null} إن لم يكن داخل واحدة.
 *
 * <p>{@link com.codejava.center.core.tenant.TenantContext} يُسقط العمل حين لا مؤسسة،
 * وذلك صوابه: استعلامٌ خارج نطاق مؤسسة عطلٌ في البرنامج، وإعطاؤه قاعدةً <i>ما</i>
 * يحوّله إلى بيانات إنسانٍ آخر على شاشة. لكن بعض الأسئلة ليست استعلامات - "أيّ عملة
 * تُكتب بجوار الرقم" أو "لمن أرسل هذا التنبيه" - وسقوطُها يظهر كخطأ خمسمئة في موضع
 * لا علاقة له بالسبب.</p>
 *
 * <p>فهذه هي الصيغة التي تسأل بلا أن تُسقط، وكلُّ تركيب يجيب عنها بما يعرفه: سنترٌ
 * واحد يجيب دائماً بمؤسسته، ومنصةٌ تجيب بما ربطه {@link TenantBindingFilter} - أو
 * بلا شيء قبل الدخول.</p>
 */
@FunctionalInterface
public interface CurrentCentre {

    TenantId boundOrNull();
}
