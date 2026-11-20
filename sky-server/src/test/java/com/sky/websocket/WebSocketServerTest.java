package com.sky.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServerTest {

    private final WebSocketServer server = new WebSocketServer();
    private final List<String> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(server::onClose);
    }

    @Test
    void merchantsConnectingDuringBroadcastsDoNotBreakThem() throws Exception {
        open("steady", session(mock(RemoteEndpoint.Basic.class)));
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            sessions.add(session(mock(RemoteEndpoint.Basic.class)));
        }
        AtomicBoolean done = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> churn = pool.submit(() -> {
                for (int i = 0; i < 20_000; i++) {
                    server.onOpen(sessions.get(i % 50), "churn-" + (i % 50));
                    server.onClose("churn-" + ((i + 25) % 50));
                }
                done.set(true);
            });
            Future<?> broadcasts = pool.submit(() -> {
                while (!done.get()) {
                    server.sendToAllClient("{\"type\":1}");
                }
            });
            churn.get(30, TimeUnit.SECONDS);
            // With a HashMap this throws ConcurrentModificationException out of the broadcast loop.
            broadcasts.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            for (int i = 0; i < 50; i++) {
                server.onClose("churn-" + i);
            }
        }
    }

    @Test
    void concurrentAlertsToOneMerchantAreSentOneAtATime() throws Exception {
        AtomicInteger writing = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        doAnswer(invocation -> {
            if (writing.incrementAndGet() > 1) {
                overlapped.set(true); // Tomcat would throw TEXT_FULL_WRITING here
            }
            Thread.sleep(1);
            writing.decrementAndGet();
            return null;
        }).when(remote).sendText(anyString());
        open("merchant-1", session(remote));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> payments = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            payments.add(pool.submit(() -> {
                start.await();
                server.sendToAllClient("{\"type\":1}");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> payment : payments) {
            payment.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertFalse(overlapped.get(), "two alerts were written to the same session at once");
    }

    @Test
    void oneBrokenConnectionDoesNotStopTheOthers() throws Exception {
        RemoteEndpoint.Basic broken = mock(RemoteEndpoint.Basic.class);
        doThrow(new IllegalStateException("connection reset")).when(broken).sendText(anyString());
        RemoteEndpoint.Basic healthy = mock(RemoteEndpoint.Basic.class);
        open("broken", session(broken));
        open("healthy", session(healthy));

        server.sendToAllClient("{\"type\":1}");

        verify(healthy).sendText("{\"type\":1}");
    }

    @Test
    void closedConnectionsAreSkipped() throws Exception {
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        Session closed = session(remote);
        when(closed.isOpen()).thenReturn(false);
        open("closed", closed);

        server.sendToAllClient("{\"type\":1}");

        assertEquals(0, mockingDetails(remote).getInvocations().size());
    }

    private void open(String sid, Session session) {
        server.onOpen(session, sid);
        opened.add(sid);
    }

    private static Session session(RemoteEndpoint.Basic remote) {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        return session;
    }
}
