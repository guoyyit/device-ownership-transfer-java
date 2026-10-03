package com.github.guoyyit.deviceownershiptransferjava.store;

import com.github.guoyyit.deviceownershiptransferjava.domain.AuditRecord;
import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Session;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PersistentState {
    public Map<String, Device> devices = new HashMap<>();
    public Map<String, Transfer> transfers = new HashMap<>();
    public Map<String, Session> sessions = new HashMap<>();
    public Map<String, OwnerAccount> owners = new HashMap<>();
    public List<AuditRecord> auditLog = new ArrayList<>();
    public Map<String, Long> usedNonces = new HashMap<>();
    public Set<String> consumedCredentials = new HashSet<>();
    public String issuerPrivateKey;
    public String issuerPublicKey;

    public PersistentState() {
    }
}
