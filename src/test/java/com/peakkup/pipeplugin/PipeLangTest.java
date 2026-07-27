package com.peakkup.pipeplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * เทสต์การอ่านข้อความจาก lang.yml โดยเฉพาะ<b>การตกไปใช้ไฟล์ที่ฝังมากับ jar</b>
 *
 * <p>สำคัญเพราะเซิร์ฟที่อัปเดตปลั๊กอินจะมี lang.yml ของเดิมอยู่แล้ว (เราไม่เขียนทับ)
 * ทุก key ที่เพิ่มมาในเวอร์ชันใหม่จึงไม่มีในไฟล์ของแอดมิน ถ้าไม่ตกไปอ่านของที่ฝังมา
 * console จะพ่นชื่อ key ดิบ ๆ ออกมาแทนข้อความ — ซึ่งเคยเกิดขึ้นจริงตอนรันบนเซิร์ฟทดสอบ
 */
class PipeLangTest {

    private static PipeLang langWith(Path folder, String adminFile, String bundled)
            throws Exception {
        Files.writeString(folder.resolve("lang.yml"), adminFile, StandardCharsets.UTF_8);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getResource("lang.yml")).thenReturn(
                new ByteArrayInputStream(bundled.getBytes(StandardCharsets.UTF_8)));
        return new PipeLang(plugin);
    }

    @Test
    void aKeyTheAdminTranslatedWins(@TempDir Path folder) throws Exception {
        PipeLang lang = langWith(folder,
                "plugin-enabled: \"เปิดใช้งานบน {platform}\"\n",
                "plugin-enabled: \"Enabled on platform: {platform}\"\n");
        assertEquals("เปิดใช้งานบน Folia", lang.msg("plugin-enabled", "platform", "Folia"));
    }

    @Test
    void aKeyAddedInANewerVersionFallsBackToTheBundledText(@TempDir Path folder) throws Exception {
        // ไฟล์ของแอดมินเป็นของเวอร์ชันเก่า ไม่มี key ใหม่นี้
        PipeLang lang = langWith(folder,
                "plugin-enabled: \"Enabled\"\n",
                "plugin-enabled: \"Enabled\"\nconfig:\n  allowed-containers: \"Allowed containers: {value}\"\n");
        assertEquals("Allowed containers: all",
                lang.msg("config.allowed-containers", "value", "all"));
    }

    @Test
    void aNestedKeyMissingFromAnExistingSectionAlsoFallsBack(@TempDir Path folder) throws Exception {
        // แอดมินมีหัวข้อ config: อยู่แล้ว แต่ยังไม่มี key ลูกตัวใหม่
        PipeLang lang = langWith(folder,
                "config:\n  unknown-container: \"nope\"\n",
                "config:\n  unknown-container: \"nope\"\n  allowed-containers: \"Allowed containers: {value}\"\n");
        assertEquals("Allowed containers: CHEST",
                lang.msg("config.allowed-containers", "value", "CHEST"));
    }

    @Test
    void aKeyInNeitherFileFallsBackToTheKeyItself(@TempDir Path folder) throws Exception {
        PipeLang lang = langWith(folder, "a: \"b\"\n", "a: \"b\"\n");
        assertEquals("nothing.here", lang.msg("nothing.here"));
    }

    @Test
    void placeholdersAreReplacedEverywhereTheyAppear(@TempDir Path folder) throws Exception {
        PipeLang lang = langWith(folder,
                "m: \"{a} then {b} then {a}\"\n", "m: \"{a} then {b} then {a}\"\n");
        assertEquals("1 then 2 then 1", lang.msg("m", "a", "1", "b", "2"));
    }

    @Test
    void everyMessageKeyUsedInTheCodeExistsInTheBundledFile(@TempDir Path folder) throws Exception {
        // กันลืมเพิ่ม key ตอนเพิ่มข้อความใหม่ — ถ้าลืม console จะพ่นชื่อ key ดิบออกมา
        String bundled = Files.readString(
                new File("src/main/resources/lang.yml").toPath(), StandardCharsets.UTF_8);
        PipeLang lang = langWith(folder, "", bundled);
        for (String key : new String[] {
                "plugin-enabled",
                "config.unknown-container", "config.allowed-containers",
                "external-storage.hooked", "external-storage.hook-failed",
                "external-storage.foreign-inventory",
                "transfer.frame-scan-failed", "transfer.schedule-failed",
                "transfer.extract-task-failed", "transfer.extract-phase-failed",
                "transfer.insert-phase-error", "transfer.insert-failed",
                "transfer.return-failed", "transfer.return-schedule-failed",
                "transfer.source-not-conserved", "transfer.dest-not-conserved"}) {
            org.junit.jupiter.api.Assertions.assertNotEquals(key, lang.msg(key),
                    "lang.yml is missing '" + key + "'");
        }
    }
}
