package com.peakkup.pipeplugin;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * เทสต์ {@link RegionExecutor} — จุดที่ทุก chain ของรอบย้ายของพึ่งพาว่า future "จบเสมอ"
 *
 * <p>ถ้า future ค้างแม้ครั้งเดียว ขั้นที่ต่อ chain ไว้ (คืนของ / release) จะไม่เคยรัน ท่อจะ busy ถาวร
 * จึงต้องการันตีทั้งทางรันทันทีและทาง schedule ว่า body ที่โยน exception ได้ future ที่ fail
 */
class RegionExecutorTest {

    private final Location loc = new Location(null, 0, 64, 0);

    @SuppressWarnings("unchecked")
    private static Consumer<WrappedTask> capturedTask(PlatformScheduler scheduler, Location loc) {
        ArgumentCaptor<Consumer<WrappedTask>> task = ArgumentCaptor.forClass(Consumer.class);
        verify(scheduler).runAtLocation(eq(loc), task.capture());
        return task.getValue();
    }

    private static PlatformScheduler scheduler(FoliaLib foliaLib, boolean owned) {
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(foliaLib.getScheduler()).thenReturn(scheduler);
        when(scheduler.isOwnedByCurrentRegion(any(Location.class))).thenReturn(owned);
        when(scheduler.runAtLocation(any(Location.class), any())).thenReturn(new CompletableFuture<>());
        return scheduler;
    }

    @Test
    void ownedRegionRunsImmediately() {
        FoliaLib foliaLib = mock(FoliaLib.class);
        PlatformScheduler scheduler = scheduler(foliaLib, true);
        AtomicBoolean ran = new AtomicBoolean();

        CompletableFuture<Void> f = new RegionExecutor(foliaLib).at(loc, () -> ran.set(true));

        assertTrue(ran.get());
        assertTrue(f.isDone());
        assertFalse(f.isCompletedExceptionally());
        verify(scheduler, never()).runAtLocation(any(Location.class), any());
    }

    @Test
    void ownedRegionFailureBecomesFailedFuture() {
        FoliaLib foliaLib = mock(FoliaLib.class);
        scheduler(foliaLib, true);

        CompletableFuture<Void> f = new RegionExecutor(foliaLib).at(loc, () -> {
            throw new IllegalStateException("boom");
        });

        assertTrue(f.isCompletedExceptionally());
    }

    @Test
    void scheduledBodyCompletesFutureWhenItRuns() {
        FoliaLib foliaLib = mock(FoliaLib.class);
        PlatformScheduler scheduler = scheduler(foliaLib, false);
        AtomicBoolean ran = new AtomicBoolean();

        CompletableFuture<Void> f = new RegionExecutor(foliaLib).at(loc, () -> ran.set(true));
        assertFalse(f.isDone(), "ยังไม่ถึงคิวของ region -> ต้องยังไม่จบ");

        capturedTask(scheduler, loc).accept(mock(WrappedTask.class));

        assertTrue(ran.get());
        assertTrue(f.isDone());
        assertFalse(f.isCompletedExceptionally());
    }

    @Test
    void scheduledBodyFailureStillCompletesFuture() {
        // FoliaLib complete future ของมันหลัง body จบปกติเท่านั้น — body ที่โยนจะทำให้มันค้างตลอดไป
        // future ที่ RegionExecutor คืนต้องไม่ใช่ตัวนั้น และต้องจบแบบ fail แทน
        FoliaLib foliaLib = mock(FoliaLib.class);
        PlatformScheduler scheduler = scheduler(foliaLib, false);
        IllegalStateException boom = new IllegalStateException("boom");

        CompletableFuture<Void> f = new RegionExecutor(foliaLib).at(loc, () -> {
            throw boom;
        });
        capturedTask(scheduler, loc).accept(mock(WrappedTask.class));

        assertTrue(f.isCompletedExceptionally());
        Throwable cause = f.handle((v, e) -> e).join();
        assertSame(boom, cause);
    }
}
