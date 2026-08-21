package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, unique = true)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 2000)
    private String value;
}
