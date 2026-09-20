package com.codejava.center.domain;

import com.codejava.center.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    // EnumType.STRING يخزّن نفس القيم النصية السابقة ("ADMIN"/"SECRETARY")
    // فلا يحتاج العمود في قاعدة البيانات أي تغيير
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
}