package com.analoghub.platform.iam.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import com.analoghub.platform.iam.dto.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "iam_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private RoleCode code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
