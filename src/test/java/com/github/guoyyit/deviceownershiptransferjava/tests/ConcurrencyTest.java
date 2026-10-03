package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.domain.TransferStatus;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentInitiationsAreSerializedToSinglePendingTransfer() throws Exception {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount seller = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, seller);
        String token = services.sessionToken(seller);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Transfer> successes = new CopyOnWriteArrayList<>();
        List<ApiException> conflicts = new CopyOnWriteArrayList<>();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    successes.add(services.transfers.initiate("device-1", buyer.ownerId, token,
                            services.uniqueNonce(), services.now()));
                } catch (ApiException e) {
                    if (e.code() == ErrorCode.TRANSFER_CONFLICT) {
                        conflicts.add(e);
                    } else {
                        unexpected.add(e);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(unexpected.isEmpty(), "unexpected errors: " + unexpected);
        assertEquals(1, successes.size(), "exactly one transfer may be created");
        assertEquals(threads - 1, conflicts.size());
        assertEquals(seller.ownerId, services.devices.currentOwner("device-1"));
        assertEquals(successes.get(0).transferId,
                services.transfers.pendingForDevice("device-1").transferId);
    }

    @Test
    void cancelAndConfirmRaceHasDeterministicOutcome() throws Exception {
        for (int round = 0; round < 20; round++) {
            TestServices services = TestServices.fresh(tempDir.resolve("round-" + round));
            OwnerAccount seller = services.newOwner();
            OwnerAccount buyer = services.newOwner();
            KeyPair keys = services.newDeviceKeys();
            services.registerDevice("device-1", keys, seller);
            String token = services.sessionToken(seller);
            Transfer transfer = services.transfers.initiate("device-1", buyer.ownerId, token,
                    services.uniqueNonce(), services.now());

            CountDownLatch start = new CountDownLatch(1);
            List<String> outcomes = new CopyOnWriteArrayList<>();
            Runnable confirmTask = () -> {
                try {
                    start.await();
                    String nonce = services.uniqueNonce();
                    long timestamp = services.now();
                    String signature = services.sign(keys, services.confirmPayload(transfer, nonce, timestamp));
                    services.transfers.confirm(transfer.transferId, "device-1", signature, nonce, timestamp);
                    outcomes.add("CONFIRMED");
                } catch (ApiException e) {
                    outcomes.add("CONFIRM_REJECTED:" + e.code());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            Runnable cancelTask = () -> {
                try {
                    start.await();
                    services.transfers.cancel(transfer.transferId, token,
                            services.uniqueNonce(), services.now());
                    outcomes.add("CANCELLED");
                } catch (ApiException e) {
                    outcomes.add("CANCEL_REJECTED:" + e.code());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<?> confirmFuture = pool.submit(confirmTask);
            Future<?> cancelFuture = pool.submit(cancelTask);
            start.countDown();
            confirmFuture.get(30, TimeUnit.SECONDS);
            cancelFuture.get(30, TimeUnit.SECONDS);
            pool.shutdown();

            boolean confirmWon = outcomes.contains("CONFIRMED");
            boolean cancelWon = outcomes.contains("CANCELLED");
            assertTrue(confirmWon ^ cancelWon,
                    "exactly one of confirm/cancel must win, outcomes=" + outcomes);

            Transfer finalTransfer = services.store.state().transfers.get(transfer.transferId);
            if (confirmWon) {
                assertEquals(TransferStatus.COMPLETED, finalTransfer.status);
                assertEquals(buyer.ownerId, services.devices.currentOwner("device-1"));
            } else {
                assertEquals(TransferStatus.CANCELLED, finalTransfer.status);
                assertEquals(seller.ownerId, services.devices.currentOwner("device-1"));
            }
        }
    }
}
