package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.domain.AuditRecord;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final StateStore store;

    public AuditService(StateStore store) {
        this.store = store;
    }

    public void record(String deviceId, String transferId, String action, String actor, String detail) {
        synchronized (store) {
            AuditRecord record = new AuditRecord();
            record.id = UUID.randomUUID().toString();
            record.timestamp = System.currentTimeMillis();
            record.deviceId = deviceId;
            record.transferId = transferId;
            record.action = action;
            record.actor = actor;
            record.detail = detail;
            store.state().auditLog.add(record);
            store.save();
        }
    }

    public List<AuditRecord> forDevice(String deviceId) {
        synchronized (store) {
            List<AuditRecord> result = new ArrayList<>();
            for (AuditRecord record : store.state().auditLog) {
                if (record.deviceId != null && record.deviceId.equals(deviceId)) {
                    result.add(record);
                }
            }
            return result;
        }
    }
}
