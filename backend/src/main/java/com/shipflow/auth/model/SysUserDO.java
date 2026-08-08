package com.shipflow.auth.model;

/** User account projection used by the login identity service. */
public final class SysUserDO {

    private final Long id;
    private final Long tenantId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final String status;
    private final String tenantStatus;

    public SysUserDO(Long id, Long tenantId, String username, String displayName,
                     String passwordHash, String status, String tenantStatus) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.status = status;
        this.tenantStatus = tenantStatus;
    }

    public Long id() {
        return id;
    }

    public Long tenantId() {
        return tenantId;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String status() {
        return status;
    }

    public String tenantStatus() {
        return tenantStatus;
    }

    @Override
    public String toString() {
        return "SysUserDO{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", status='" + status + '\'' +
                ", tenantStatus='" + tenantStatus + '\'' +
                '}';
    }
}
