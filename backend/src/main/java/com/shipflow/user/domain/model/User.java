package com.shipflow.user.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public final class User {
    private final Long id; private final Long tenantId; private final String username; private final String displayName;
    private final String status; private final long version; private final LocalDateTime createdAt; private final LocalDateTime updatedAt;
    private final List<Long> roleIds;
    public User(Long id, Long tenantId, String username, String displayName, String status, long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id,tenantId,username,displayName,status,version,List.of(),createdAt,updatedAt);
    }
    public User(Long id, Long tenantId, String username, String displayName, String status, long version, List<Long> roleIds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id=id; this.tenantId=tenantId; this.username=username; this.displayName=displayName; this.status=status; this.version=version; this.roleIds=roleIds==null?List.of():List.copyOf(roleIds); this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public Long id(){return id;} public Long tenantId(){return tenantId;} public String username(){return username;} public String displayName(){return displayName;}
    public String status(){return status;} public long version(){return version;} public List<Long> roleIds(){return roleIds;} public LocalDateTime createdAt(){return createdAt;} public LocalDateTime updatedAt(){return updatedAt;}
}
