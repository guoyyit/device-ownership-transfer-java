package com.github.guoyyit.deviceownershiptransferjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author Vincent
 * @version 1.0
 */
@SpringBootTest(properties = "app.store.path=target/test-data/app/state.json")
public class ApplicationTests {
    @Test
    public void contentLoad() {
    }
}
