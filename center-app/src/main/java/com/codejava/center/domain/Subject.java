package com.codejava.center.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(name = "uk_subject_name_key", columnNames = "name_key"))
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Subject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @EqualsAndHashCode.Include
    private Long id;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;
    public Subject(String name, String nameKey) { this.name = name; this.nameKey = nameKey; }
}
