package com.peakkup.pipeplugin;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;

import java.util.Objects;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * เซิร์ฟเวอร์จำลองขั้นต่ำสุด เพื่อให้ {@link org.bukkit.inventory.ItemStack#isSimilar} ใช้ได้ในเทสต์ยูนิต
 *
 * <p>ทำไมต้องมี: การจับคู่ไอเทมภายใน (ดูด/นับ/คืนของ) เทียบแบบเป๊ะด้วย {@code isSimilar} เสมอ
 * ซึ่งข้างในเรียก {@code Bukkit.getItemFactory()} — ถ้าไม่มีเซิร์ฟเวอร์ตั้งไว้จะได้ NullPointerException
 * ทันที เทสต์ชุดเดิมจึงเคยเลี่ยงไปใช้ {@code MatchMode.TYPE} แทน แต่ตอนนี้โค้ดจริงไม่ใช้โหมดนั้น
 * ในการจัดกลุ่มของแล้ว (ดูเหตุผลใน {@link InventoryOps}) เทสต์จึงต้องเทียบด้วยของจริง
 *
 * <p>ItemFactory จำลองตอบว่า "ไอเทมไม่มี meta" ทุกชิ้น ({@code getItemMeta} คืน null และ
 * {@code equals(null, null)} เป็น true) ผลคือ {@code isSimilar} เหลือการเทียบชนิด + durability
 * ซึ่งพอดีกับสิ่งที่เทสต์ชุดนี้ต้องการ (ทดสอบการจัดการช่อง ไม่ใช่การเทียบ NBT)
 */
final class BukkitStub {

    private static boolean installed;

    private BukkitStub() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        if (Bukkit.getServer() != null) {
            return; // มีคนตั้งไว้แล้ว (setServer ตั้งซ้ำไม่ได้)
        }

        ItemFactory factory = mock(ItemFactory.class);
        lenient().when(factory.getItemMeta(any())).thenReturn(null);
        lenient().when(factory.equals(any(), any()))
                .thenAnswer(call -> Objects.equals(call.getArgument(0), call.getArgument(1)));

        Server server = mock(Server.class);
        lenient().when(server.getItemFactory()).thenReturn(factory);
        lenient().when(server.getLogger()).thenReturn(Logger.getLogger("BukkitStub"));
        Bukkit.setServer(server);
    }
}
