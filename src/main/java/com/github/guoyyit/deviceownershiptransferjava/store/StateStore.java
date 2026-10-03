package com.github.guoyyit.deviceownershiptransferjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class StateStore {

    private final Path storePath;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile PersistentState state;

    public StateStore(@Value("${app.store.path:./data/state.json}") String storePath) {
        this.storePath = Path.of(storePath).toAbsolutePath();
    }

    @PostConstruct
    public void load() {
        synchronized (this) {
            if (Files.exists(storePath)) {
                try {
                    state = mapper.readValue(storePath.toFile(), PersistentState.class);
                } catch (IOException e) {
                    throw new IllegalStateException("failed to load state from " + storePath, e);
                }
            } else {
                state = new PersistentState();
                save();
            }
        }
    }

    public PersistentState state() {
        PersistentState current = state;
        if (current == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "state store not initialized");
        }
        return current;
    }

    public void save() {
        synchronized (this) {
            try {
                Path parent = storePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Path temp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
                mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
                Files.move(temp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "failed to persist state");
            }
        }
    }
}
