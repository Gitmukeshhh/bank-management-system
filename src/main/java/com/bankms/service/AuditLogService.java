package com.bankms.service;

import com.bankms.entity.AuditLog;
import com.bankms.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(String actorUsername, String action, String entityType, Long entityId, String details) {
        auditLogRepository.save(AuditLog.builder()
                .actorUsername(actorUsername)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build());
    }
}
