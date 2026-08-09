package com.shipflow.user.application;

import com.shipflow.user.api.model.*;
import com.shipflow.user.domain.model.*;
import com.shipflow.user.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserApplicationService {
    private static final String CREATE = "createUser";
    private final UserMapper mapper; private final UserIdempotencyMapper idem; private final UserAuditMapper audit;
    private final PasswordEncoder encoder; private final Clock clock;
    public UserApplicationService(UserMapper mapper, UserIdempotencyMapper idem, UserAuditMapper audit, PasswordEncoder encoder, Clock clock) {
        this.mapper=mapper; this.idem=idem; this.audit=audit; this.encoder=encoder; this.clock=clock;
    }
    @Transactional
    public User create(Long tenantId, CreateUserRequest r, String key, Long operator, String requestId) {
        requireKey(key); String hash = digest(r.username()+"\n"+r.displayName()+"\n"+r.temporaryPassword()+"\n"+r.roleIds());
        UserIdempotencyMapper.Record prior=idem.find(tenantId,CREATE,key);
        if(prior!=null){ if(!hash.equals(prior.requestHash())) throw error("COMMON-1009",409); if(prior.resourceId()!=null)return withRoles(tenantId, required(tenantId,prior.resourceId())); throw error("COMMON-1010",409); }
        try {
            idem.insert(tenantId,CREATE,key,hash,LocalDateTime.now(clock).plusMinutes(30));
            if (mapper.findByUsername(tenantId,r.username())!=null) throw error("USER-1001",409);
            if (!r.roleIds().isEmpty() && mapper.roleCount(tenantId,r.roleIds()) != r.roleIds().stream().distinct().count()) throw error("ROLE-1002",404);
            mapper.insert(tenantId,r.username(),r.displayName(),encoder.encode(r.temporaryPassword()));
            User u=requiredByName(tenantId,r.username()); replaceRolesInternal(tenantId,u.id(),r.roleIds());
            idem.complete(tenantId,CREATE,key,u.id()); audit.insert(tenantId,operator,"CREATE",u.id(),requestId,LocalDateTime.now(clock));
            return withRoles(tenantId, required(tenantId,u.id()));
        } catch (DuplicateKeyException e) { throw error("USER-1001",409); }
    }
    public UserPage page(Long t,String status,String username,int page,int size){int p=Math.max(1,page), n=Math.min(Math.max(1,size),100);long total=mapper.count(t,status,blank(username));return new UserPage(p,n,total,(int)((total+n-1)/n),mapper.page(t,status,blank(username),(p-1)*n,n).stream().map(u->withRoles(t,u)).toList());}
    public User get(Long t,Long id){return withRoles(t,required(t,id));}
    @Transactional public User update(Long t,Long id,UpdateUserRequest r,String key,Long op,String req){
        return idempotentUpdate(t,id,r.displayName(),r.version(),key,op,req,()->mapper.updateName(t,id,r.displayName(),r.version()));
    }
    @Transactional public User status(Long t,Long id,UserStatusChangeRequest r,Long op,String req){
        if(!"ACTIVE".equals(r.status())&&!"DISABLED".equals(r.status()))throw error("USER-1002",422);
        if(mapper.updateStatus(t,id,r.status(),r.version())!=1)throw conflict(t,id); User u=required(t,id);audit.insert(t,op,"STATUS_CHANGE",id,req,LocalDateTime.now(clock));return withRoles(t,u);
    }
    @Transactional public User roles(Long t,Long id,UserRoleBindingRequest r,Long op,String req){
        User u=required(t,id); if(!r.roleIds().isEmpty() && mapper.roleCount(t,r.roleIds())!=r.roleIds().stream().distinct().count())throw error("ROLE-1002",404);
        if(mapper.updateName(t,id,u.displayName(),r.version())!=1)throw conflict(t,id); replaceRolesInternal(t,id,r.roleIds());
        User result=required(t,id); audit.insert(t,op,"ROLE_BIND",id,req,LocalDateTime.now(clock)); return withRoles(t,result);
    }
    private User idempotentUpdate(Long t,Long id,String name,long version,String key,Long op,String req,Runnable update){
        if(key==null||key.isBlank())throw error("COMMON-1001",400); String h=digest(id+"\n"+name+"\n"+version);UserIdempotencyMapper.Record p=idem.find(t,"updateUser",key);
        if(p!=null){if(!h.equals(p.requestHash()))throw error("COMMON-1009",409);return withRoles(t, required(t,p.resourceId()));}
        idem.insert(t,"updateUser",key,h,LocalDateTime.now(clock).plusMinutes(30)); required(t,id); update.run(); if(mapper.findById(t,id)==null)throw conflict(t,id);idem.complete(t,"updateUser",key,id);audit.insert(t,op,"UPDATE",id,req,LocalDateTime.now(clock));return withRoles(t,required(t,id));
    }
    private void replaceRolesInternal(Long t,Long id,List<Long> ids){mapper.deleteRoles(t,id);ids.stream().distinct().forEach(role->mapper.bindRole(t,id,role));}
    private User required(Long t,Long id){User u=mapper.findById(t,id);if(u==null)throw error("COMMON-1006",404);return u;}
    private User withRoles(Long t,User u){return new User(u.id(),u.tenantId(),u.username(),u.displayName(),u.status(),u.version(),mapper.roleIds(t,u.id()),u.createdAt(),u.updatedAt());}
    private User requiredByName(Long t,String n){User u=mapper.findByUsername(t,n);if(u==null)throw error("COMMON-1007",500);return u;}
    private String blank(String s){return s==null||s.isBlank()?null:s;}
    private void requireKey(String k){if(k==null||k.isBlank())throw error("COMMON-1001",400);}
    private UserException conflict(Long t,Long id){return mapper.findById(t,id)==null?error("COMMON-1006",404):error("COMMON-1005",409);}
    private UserException error(String c,int s){return new UserException(c,s);}
    private String digest(String s){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
