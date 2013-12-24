package com.gdiama.cache;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Fail.fail;
import static org.mockito.Mockito.*;

public class WriteBehindCacheTest {

    private final ShutdownService service = mock(ShutdownService.class);
    private final AppConfiguration config = mock(AppConfiguration.class);
    private final ExecCommand command = mock(ExecCommand.class);
    private final MetricRegistry metricRegistry = mock(MetricRegistry.class);
    private final TimeUnit flushTimeUnit = TimeUnit.MICROSECONDS;
    private final long flushInterval = 5L;
    private final String someObject = "someObject";

    private WriteBehindCache<Object> cache;

    @Before
    public void setup() {
        when(config.initDelayWriteBehindCacheInMillis()).thenReturn(flushInterval);
        when(config.writeBehindFlushInterval()).thenReturn(flushInterval);
        when(config.writeBehindFlushTimeUnit()).thenReturn(flushTimeUnit);

        when(metricRegistry.counter(someObject + ".writebehindcache.storage.counter")).thenReturn(new Counter());
        when(metricRegistry.meter(someObject + ".writebehindcache.storage.meter")).thenReturn(new Meter());
        when(metricRegistry.timer(someObject + ".writebehindcache.storage.timer")).thenReturn(new Timer());

        when(metricRegistry.counter(someObject + ".writebehindcache.flushed.counter")).thenReturn(new Counter());
        when(metricRegistry.meter(someObject + ".writebehindcache.flushed.meter")).thenReturn(new Meter());
        when(metricRegistry.timer(someObject + ".writebehindcache.flushed.timer")).thenReturn(new Timer());


        cache = new WriteBehindCache<Object>(command, someObject, config, service, metricRegistry);
    }

    @Test
    public void store() throws Exception {
        Object obj = new Object();

        cache.store(obj);
        waitForFlush();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(command).run(captor.capture());

        assertThat(captor.getValue()).isSameAs(obj);
    }

    @Test
    public void throwExceptionWhenAttemptingToStoreOnShutdown() throws Exception {
        cache.store(new Object());

        flushTimeUnit.sleep(2 * flushInterval);

        final CountDownLatch latch2 = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cache.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch2.countDown();
                }
            }
        }).start();

        latch2.await();

        int noOfObjects = 100;
        CountDownLatch latch3 = new CountDownLatch(noOfObjects);
        List<RuntimeException> ex = new ArrayList<>();
        for (int i = 0; i < noOfObjects; i++) {
            try {
                cache.store(new Object());
            } catch (RuntimeException e) {
                ex.add(e);
            } finally {
                latch3.countDown();
            }
        }

        latch3.await();

        verify(command, times(1)).run(any(Object.class));
        verifyNoMoreInteractions(command);
        assertThat(ex).hasSize(noOfObjects);
    }

    @Test
    public void storeFloodOfEventsAndFlush() throws InterruptedException {
        final int noOfObjects = 100;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(noOfObjects);

        final BlockingQueue<Object> capturedObjects = new LinkedBlockingQueue<>();
        final AtomicInteger counter = new AtomicInteger(0);
        WriteBehindCache<TestObj> cache = new WriteBehindCache<>(new ExecCommand() {
            @Override
            public <T> void run(T event) {
                try {
                    capturedObjects.put(event);
                    stop.countDown();
                    counter.incrementAndGet();
                } catch (InterruptedException e) {
                    fail("Unable to put object", e);
                }
            }
        }, someObject, config, service, metricRegistry);

        List<TestObj> objs = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(50);

        for (int i = 0; i < noOfObjects; i++) {
            TestObj testObj = new TestObj();
            objs.add(testObj);
            executorService.submit(new StoreEventTask(cache, testObj, start));
        }

        start.countDown();
        stop.await(200, TimeUnit.MILLISECONDS);

        assertThat(capturedObjects).describedAs("Actual size: " + capturedObjects.size()).hasSize(noOfObjects).containsAll(objs);
    }

    public static class StoreEventTask implements Callable<Void> {

        private final WriteBehindCache<TestObj> cache;
        private final TestObj event;
        private final CountDownLatch start;

        public StoreEventTask(WriteBehindCache<TestObj> cache, TestObj testObj, CountDownLatch start) {
            this.cache = cache;
            this.event = testObj;
            this.start = start;
        }

        @Override
        public Void call() throws Exception {
            start.await();
            cache.store(event);
            return null;
        }
    }

    private void waitForFlush() throws InterruptedException {
        flushTimeUnit.sleep(flushInterval + 1);
    }

    private static class TestObj {
        final UUID uuid = UUID.randomUUID();


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestObj testObj = (TestObj) o;

            if (uuid != null ? !uuid.equals(testObj.uuid) : testObj.uuid != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return uuid != null ? uuid.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "TestObj{" +
                    "uuid=" + uuid +
                    '}';
        }
    }
}
