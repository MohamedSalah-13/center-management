package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * إعدادات السنتر.
 *
 * <p>كانت شاشة الإعدادات تتعامل مع الـ Repository مباشرةً، وكان كل من يحتاج اسم السنتر
 * يكرّر {@code findById(1L)} ومعالجة غيابه. الصف يوحّد ذلك ويضمن أن صف الإعدادات
 * واحد دائماً بالمعرّف 1.</p>
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final CenterSettingsRepository centerSettingsRepository;

    /** الإعدادات الحالية، أو صف افتراضي غير محفوظ إن لم تُضبط بعد */
    @Transactional(readOnly = true)
    public CenterSettings getSettings() {
        return centerSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> CenterSettings.builder().id(SETTINGS_ID).build());
    }

    /** اسم السنتر للعرض، مع بديل معقول قبل ضبط الإعدادات */
    @Transactional(readOnly = true)
    public String getCenterName() {
        String name = getSettings().getCenterName();
        // الاسم الافتراضي يتبع لغة الواجهة؛ الاسم المضبوط في الإعدادات يُعرض كما كتبه المستخدم
        return name == null || name.isBlank() ? I18n.get("settings.defaultCenterName") : name;
    }

    /**
     * المعرّف يُفرض على 1 دائماً: الإعدادات صف واحد، وتركه لما يأتي من الواجهة
     * يسمح بإنشاء صفوف إعدادات متعددة لا يقرأ النظام إلا أولها.
     */
    @Transactional
    public CenterSettings save(CenterSettings settings) {
        settings.setId(SETTINGS_ID);
        return centerSettingsRepository.save(settings);
    }
}
