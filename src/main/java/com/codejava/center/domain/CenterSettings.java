package com.codejava.center.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "center_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CenterSettings {

    @Id
    private Long id = 1L; // تثبيت الـ ID برقم 1 لأننا نحتاج صف واحد فقط للإعدادات

    private String centerName;
    private String centerPhone;
    private String logoPath;
    private String backupPath;
    private boolean autoBackupEnabled;
}