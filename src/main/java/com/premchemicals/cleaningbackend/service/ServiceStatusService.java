package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.model.SystemSetting;
import com.premchemicals.cleaningbackend.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceStatusService {

    private final SystemSettingRepository repository;
    private volatile boolean serviceSuspended = false;

    @PostConstruct
    public void init() {
        serviceSuspended = repository.findById("service_suspended")
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(false);
    }

    public boolean isServiceSuspended() {
        return serviceSuspended;
    }

    public synchronized void setServiceSuspended(boolean suspended) {
        this.serviceSuspended = suspended;
        SystemSetting setting = repository.findById("service_suspended")
                .orElseGet(() -> SystemSetting.builder().key("service_suspended").build());
        setting.setValue(String.valueOf(suspended));
        repository.save(setting);
    }
}
