package com.example.ecommerce.audit;
import com.example.ecommerce.auth.AppUser; import com.fasterxml.jackson.core.JsonProcessingException; import com.fasterxml.jackson.databind.ObjectMapper; import jakarta.servlet.http.HttpServletRequest; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service
public class AdminAuditService {
    private final AdminAuditLogRepository logs; private final ObjectMapper objectMapper;
    public AdminAuditService(AdminAuditLogRepository logs, ObjectMapper objectMapper){ this.logs=logs; this.objectMapper=objectMapper; }
    @Transactional
    public void log(AppUser admin, String action, String entityType, String entityId, Object oldValue, Object newValue, HttpServletRequest request){ logs.save(new AdminAuditLog(admin.getId(), admin.getEmail(), action, entityType, entityId, toJson(oldValue), toJson(newValue), request.getRemoteAddr())); }
    private String toJson(Object value){ if(value==null) return null; try { return objectMapper.writeValueAsString(value); } catch(JsonProcessingException e){ return "{"serializationError":true}"; } }
}
