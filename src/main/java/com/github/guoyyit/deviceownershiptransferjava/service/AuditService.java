package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.store.AuditEntry;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final StateStore stateStore;

    public AuditService(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void record(String eventType, String deviceId, String actorId, String detail) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.eventType = eventType;
        entry.deviceId = deviceId;
        entry.actorId = actorId;
        entry.detail = detail;
        stateStore.appendAudit(entry);
    }

    public List<AuditEntry> listForDevice(String deviceId) {
        return stateStore.listAudit(deviceId);
    }
}
