package com.codejava.center.web.api;

import com.codejava.center.security.AccessDeniedException;
import com.codejava.center.util.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return of(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * ما لا اسم له: يُسجَّل كاملاً هنا ويصل إلى الشاشة برسالة عامة.
     *
     * <p>نصُّ الاستثناء الخام يحمل أسماء جداول وأعمدة وأحياناً جزءاً من استعلام،
     * وهي خريطةٌ مجانية لمن يجمعها.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("طلب لم يكتمل: {}", e.getMessage(), e);
        return of(HttpStatus.INTERNAL_SERVER_ERROR, I18n.get("error.unexpected"));
    }

    private ResponseEntity<ApiError> of(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(),
                message == null ? status.getReasonPhrase() : message));
    }
}
