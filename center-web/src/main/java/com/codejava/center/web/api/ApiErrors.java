package com.codejava.center.web.api;

import com.codejava.center.security.AccessDeniedException;
import com.codejava.center.util.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * أخطاءُ الخدمات إلى أجوبة HTTP، برسائلها المترجمة كما هي.
 *
 * <p>طبقةُ الأعمال ترمي رسالةً بلغة المستخدم - هذا عقدها منذ أن صار
 * {@code executeBackup} يرمي بدل أن يعيد {@code false}. الخطأ هنا أن تُبتلع تلك
 * الرسالة ويُرسل "خطأ داخلي": هو بالضبط ما كان يراه العميل في نسخة jpackage بلا
 * طرفية، ولا يُقرأ منه سببٌ ولا خطوةٌ تالية.</p>
 *
 * <p>والحالات ثلاث لأن الأسباب ثلاثة: مدخلٌ خاطئ يصلحه من كتبه ({@code 400})، وحالةٌ
 * في القاعدة تمنع العملية - حصةٌ مغلقة، رصيدٌ لا يكفي - لا يصلحها تغييرُ المدخل
 * ({@code 409})، ورفضٌ لصلاحية ({@code 403}). وخلطُها في {@code 400} واحد يجعل
 * الواجهة تقول "تحقّق مما كتبت" لمن لم يخطئ في شيء.</p>
 */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public record ApiError(int status, String message) {
    }

    /**
     * الرفض يُكتب في سجل المراقبة قبل أن يصل إلى هنا - {@code RoleEnforcementAspect}
     * يكتبه ثم يرمي - فلا يُكتب مرتين.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return of(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badInput(IllegalArgumentException e) {
        return of(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElseGet(() -> I18n.get("error.validation.failed"));
        return of(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * مُعامل في الرابط لا يُفهم: صفٌّ ليس في {@code SchoolLevel}، أو تاريخٌ بصيغة أخرى.
     *
     * <p>بغير هذا يمرّ إلى المُمسك العام فيصل {@code 500} بجملة "حدث خطأ غير متوقع" -
     * وهو ليس غير متوقع ولا هو خطأ عندنا: من كتب الرابط يصلحه.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badParameter(MethodArgumentTypeMismatchException e) {
        return of(HttpStatus.BAD_REQUEST, e.getName() + ": " + I18n.get("error.validation.failed"));
    }

    /**
     * جسمٌ لا يُقرأ: JSON مشوَّه، أو ثابتٌ لا تعرفه القائمة - {@code "currency":"XYZ"}.
     *
     * <p>وهو لا يطبّق {@code ErrorResponse} خلافاً لإخوته، فيسقط في المُمسك العام ويصل
     * {@code 500}. والرسالةُ عامة عن قصد: نصُّ الاستثناء يحمل أسماء أصنافٍ ومواضعَ داخل
     * الـ JSON، وهي وصفٌ لبنية الخادم لمن يجمعها.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return of(HttpStatus.BAD_REQUEST, I18n.get("error.validation.failed"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return of(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * ما لا اسم له: يُسجَّل كاملاً هنا ويصل إلى الشاشة برسالة عامة.
     *
     * <p>نصُّ الاستثناء الخام يحمل أسماء جداول وأعمدة وأحياناً جزءاً من استعلام،
     * وهي خريطةٌ مجانية لمن يجمعها.</p>
     *
     * <p><b>إلا ما يحمل حالتَه معه.</b> استثناءاتُ إطار الويب - طريقةٌ لا يقبلها المسار،
     * جسمٌ لا يُقرأ، نوعُ محتوى غير مدعوم - تُطبّق {@code ErrorResponse} وتحمل كلٌّ منها
     * رمزَها الصحيح. وابتلاعُها في {@code 500} يجعل "طريقة غير مسموحة" خطأً في الخادم:
     * يُسجَّل في سجلّ الأخطاء ويوقظ من يقرؤه، ويقرؤه من أرسل الطلب على أن العيب ليس عنده.
     * وهذا ما كان يقع لكل {@code DELETE} على مسارٍ للقراءة ولكل جسمٍ مشوَّه.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse framework) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            // 4xx مدخلٌ يصلحه من كتبه، فلا يُسجَّل خطأً ولا يحمل تفاصيل الإطار
            return of(status, I18n.get(status.is4xxClientError()
                    ? "error.validation.failed"
                    : "error.unexpected"));
        }

        log.error("طلب لم يكتمل: {}", e.getMessage(), e);
        return of(HttpStatus.INTERNAL_SERVER_ERROR, I18n.get("error.unexpected"));
    }

    private ResponseEntity<ApiError> of(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(),
                message == null ? status.getReasonPhrase() : message));
    }
}
