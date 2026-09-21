package com.codejava.center.web.api;

import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.service.UserService;
import com.codejava.center.service.dto.UserDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * حسابات المستخدمين.
 *
 * <p>هذه الشاشة هي التي وُجد {@code UserDraft} لأجلها قبل أن يوجد منفذُ HTTP، ووصلت
 * الآن بلا عملٍ في طبقة الأعمال: المسودةُ سبقتها بمرحلة كاملة.</p>
 *
 * <p><b>كلمةُ المرور ليست حقلاً في المسودة، وهي حقلٌ هنا.</b> ليس تناقضاً: المسودة
 * تحمل ما يُكتب في الصفّ، والصفُّ يحمل بصمةً لا كلمة. فلو كانت فيها لَقَبِل الحفظُ بصمةً
 * جاهزة يكتبها المُرسِل - أي كلمةَ مرورٍ يعرفها هو ولا يعرفها صاحب الحساب. تصل هنا نصّاً
 * صريحاً وتُبصَم في الخدمة، ومعها تأكيدُها: خطأُ حرفٍ في كلمةٍ لا تُعرض يُقفل الحساب على
 * صاحبه.</p>
 *
 * <p>ولا تُسلَّم البصمة في أي جواب. والحدودُ الأخرى - آخرُ مدير لا يُحذف، ولا أحد يحذف
 * نفسه، وحسابُ المدير المدمج - كلُّها في {@code UserService}، وتصل هنا {@code 409}.</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * @param password     كلمةُ المرور نصّاً صريحاً؛ فارغةً في التعديل تعني "لا تغيّرها"
     * @param confirmation تكرارُها - يُفحص في الخدمة لا في المتصفّح
     */
    public record UserRequest(
            @Size(max = 50) String username,
            @NotNull Role role,
            String password,
            String confirmation) {

        UserDraft toDraft(Long id) {
            return new UserDraft(id, username, role);
        }
    }

    /** بلا بصمة، ولا حقلٍ يشبهها */
    public record UserView(Long id, String username, String roleName, String role) {
    }

    public record RoleView(String name, String label) {
    }

    @GetMapping
    public List<UserView> users() {
        return userService.getAllUsers().stream().map(UserController::view).toList();
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        return Arrays.stream(Role.values())
                .map(role -> new RoleView(role.name(), role.getDisplayName()))
                .toList();
    }

    @PostMapping
    public UserView create(@Valid @RequestBody UserRequest request) {
        return view(userService.saveUser(request.toDraft(null),
                request.password(), request.confirmation()));
    }

    /** المعرّف من المسار وحده، كما في كل مورد هنا */
    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return view(userService.saveUser(request.toDraft(id),
                request.password(), request.confirmation()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    private static UserView view(User user) {
        return new UserView(user.getId(), user.getUsername(),
                user.getRole() == null ? null : user.getRole().getDisplayName(),
                user.getRole() == null ? null : user.getRole().name());
    }
}
