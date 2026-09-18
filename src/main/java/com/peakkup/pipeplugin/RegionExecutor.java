package com.peakkup.pipeplugin;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

/**
 * รันงานบนเธรดของ region ที่เป็นเจ้าของตำแหน่งหนึ่ง ๆ — <b>ถ้าเราอยู่บน region นั้นอยู่แล้วให้รันทันที</b>
 *
 * <p>เดิมเรียก {@code foliaLib.getScheduler().runAtLocation(...)} ตรง ๆ ทุกครั้ง ซึ่งบน Spigot/Paper
 * แปลว่า {@code BukkitScheduler#runTask} = <b>เลื่อนไป tick ถัดไปเสมอ</b> แม้จะอยู่บนเธรดหลักอยู่แล้ว
 * ผลคือหนึ่ง pulse กินหลาย tick (สแกน frame → ดูด → ใส่แต่ละ output → คืนของ อย่างละ tick)
 * ทำให้ท่อทั้ง<b>ช้า</b> (ยิงถี่ ๆ จะโดน busy ตีกลับเป็นส่วนใหญ่) และ<b>เปิดช่อง</b>ให้ hopper/ผู้เล่น
 * เข้ามาแทรกช่องที่เพิ่งถูกดูดออกระหว่างรอบ
 *
 * <p>ตัวตัดสินคือ {@code isOwnedByCurrentRegion(loc)} ซึ่งบน Spigot/Paper = {@code isPrimaryThread()}
 * และบน Folia = การเช็ค region จริง จึงได้ผลที่ถูกต้องทั้งสองแบบด้วยโค้ดชุดเดียว:
 * <ul>
 *   <li>Bukkit/Paper — ทุกเฟสรันในเธรดหลัก tick เดียวกัน = ทั้งรอบเป็น atomic</li>
 *   <li>Folia — ท่อที่อยู่ใน region เดียว (เกือบทั้งหมด เพราะท่อยาวไม่เกิน max-pipe-length)
 *       ก็รันในรอบเดียวเช่นกัน เหลือแต่ท่อที่พาดข้าม region จริง ๆ ที่ต้อง schedule</li>
 * </ul>
 *
 * <p>ยังคืน {@link CompletableFuture} เหมือนเดิม ผู้เรียกจึงต่อ chain ได้แบบไม่ต้องรู้ว่ารันทันทีหรือไม่
 * และ exception จาก body ถูกห่อเป็น future ที่ fail (ไม่ throw ออกมา) เพื่อให้ผู้เรียกจัดการทางเดียวกันหมด
 */
public final class RegionExecutor {

    private final FoliaLib foliaLib;

    public RegionExecutor(FoliaLib foliaLib) {
        this.foliaLib = foliaLib;
    }

    /** true = เธรดปัจจุบันเป็นเจ้าของตำแหน่งนี้ (แตะบล็อก/inventory ตรงนั้นได้เลย) */
    public boolean ownsCurrentThread(Location loc) {
        if (loc == null) {
            return false;
        }
        try {
            return foliaLib.getScheduler().isOwnedByCurrentRegion(loc);
        } catch (Throwable t) {
            return false; // ตอบไม่ได้ -> ถือว่าไม่ใช่ของเรา แล้วไป schedule ตามปกติ
        }
    }

    /** รัน body บน region ของ loc (ทันทีถ้าเป็นของเราอยู่แล้ว) */
    public CompletableFuture<Void> at(Location loc, Runnable body) {
        if (ownsCurrentThread(loc)) {
            try {
                body.run();
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
            return CompletableFuture.completedFuture(null);
        }
        // ไม่คืน future ของ FoliaLib ตรง ๆ: มัน complete หลัง body จบปกติเท่านั้น ถ้า body โยน exception
        // future นั้นจะค้างตลอดไป -> ทุกขั้นที่ต่อ chain ไว้ไม่เคยรัน, release() ไม่ถูกเรียก = ท่อ busy ถาวร
        // (และของที่ดูดมาแล้วไม่ถูกคืน) จึงห่อเองให้ exception กลายเป็น future ที่ fail เหมือนทางรันทันที
        CompletableFuture<Void> done = new CompletableFuture<>();
        foliaLib.getScheduler().runAtLocation(loc, task -> {
            try {
                body.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }
}
