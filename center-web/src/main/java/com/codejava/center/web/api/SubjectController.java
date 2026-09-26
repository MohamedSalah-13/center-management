package com.codejava.center.web.api;
import com.codejava.center.domain.Subject;
import com.codejava.center.service.SubjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/subjects") @RequiredArgsConstructor
public class SubjectController {
    private final SubjectService service;
    public record SubjectView(Long id, String name) { }
    public record SubjectRequest(@NotBlank @Size(max=50) String name) { }
    @GetMapping public List<SubjectView> list() { return service.getAllSubjects().stream().map(SubjectController::view).toList(); }
    @PostMapping public SubjectView create(@Valid @RequestBody SubjectRequest request) { return view(service.save(null, request.name())); }
    @PutMapping("/{id}") public SubjectView update(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) { return view(service.save(id, request.name())); }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(id); }
    private static SubjectView view(Subject subject) { return new SubjectView(subject.getId(), subject.getName()); }
}
