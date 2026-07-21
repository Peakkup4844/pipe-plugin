# PipePlugin — Skill / Maintainer Guide

> ⚠️ **กฎสำคัญ / IMPORTANT:**
> **ทุกครั้งที่มีการเปลี่ยนแปลง "โครงสร้าง" ของโปรเจกต์ (เพิ่ม/ลบ/เปลี่ยนหน้าที่คลาส, เปลี่ยน flow การย้ายของ,
> เปลี่ยนกฎ routing/filter, เปลี่ยน config, เปลี่ยน dependency หรือ build) ต้องอัปเดตไฟล์ `skill.md` นี้ให้ตรงทันที**
> ถือว่าไฟล์นี้คือ "แหล่งความจริง" ของหลักการและสถาปัตยกรรม — โค้ดกับเอกสารต้องไม่ขัดกัน
> *Whenever the project structure changes, update this file in the same change.*

ไฟล์นี้สรุป **หลักการ (principles)** และ **โครงสร้าง (architecture)** สำหรับนักพัฒนา/ผู้ดูแล
(ส่วนวิธีใช้สำหรับผู้เล่นอยู่ใน [README.md](README.md))

---

## 1. เป้าหมายของปลั๊กอิน

ระบบท่อขนไอเทมสไตล์ม็อด *Create*:
- **input** = sticky piston หันหัวเข้า container (ต้นทาง)
- **process** = ท่อกระจกสีเดียวกันทั้งเส้น (กระจกแต่ละสี = เครือข่ายแยกกัน)
- **output** = piston ธรรมดาหันหัวเข้า container (ปลายทาง) — มีได้ **หลายจุดต่อ 1 ท่อ**
- จ่าย redstone **pulse** เข้า sticky piston → 1 pulse = ย้ายของ **1 ชนิด** สูงสุด `items-per-cycle` (default 32) ชิ้น; ของที่ stack ไม่ได้ = 1 ชิ้น/รอบ
- **item frame** บนบล็อกใดบล็อกหนึ่งของท่อ (กระจก / input piston / output piston) = ตัวกรอง (gate) **รายบล็อก** — ของต้อง match ถึงจะผ่าน/เข้าบล็อกนั้นได้
- มี **rate limit ต่อท่อ** (`min-pulse-interval-ticks`) + เสียง/particle ตอนย้ายสำเร็จ (`effects`)

---

## 2. กลยุทธ์ความเข้ากันได้ (อย่าทำให้พัง)

| เรื่อง | กติกา |
|---|---|
| ช่วงเวอร์ชัน | MC **1.20.1 → ล่าสุด (26.1)** ด้วย jar เดียว |
| API | **Bukkit/Spigot API ล้วน ห้าม NMS / ห้าม API เฉพาะ Paper / ห้าม API เฉพาะเวอร์ชันใหม่** |
| Compile target | `compileOnly spigot-api:1.20.1` + `options.release = 17` → ถ้าเผลอใช้ API ที่ไม่มีใน 1.20.1 จะ compile ไม่ผ่านทันที (เป็น guard rail) |
| Java | bytecode 17 (รันได้บน JVM 17→21+) |
| Folia | ผ่าน **FoliaLib** (shade + relocate → `com.peakkup.pipeplugin.libs.folialib`) |
| ไฟล์ปลั๊กอิน | `plugin.yml` แบบคลาสสิก (ไม่ใช่ `paper-plugin.yml`), `folia-supported: true` |

**ห้ามทำ:** เพิ่ม dependency ที่มีเฉพาะ Paper/Folia แบบ compile-time, เรียก method ที่เพิ่งมีในเวอร์ชันใหม่,
ใช้ `PlayerItemFrameChangeEvent` (Paper-only) — เราจึงอ่าน frame แบบ live แทน

**ระวัง enum churn:** ชื่อค่าใน `Particle`/`Sound` เปลี่ยนข้ามเวอร์ชัน (เช่น `BLOCK_CRACK`→`BLOCK`) —
เอฟเฟกต์ใน `ItemTransferService.playEffect` ใช้ค่าที่เสถียร (`Particle.CRIT`, `Sound.BLOCK_DISPENSER_DISPENSE`)
และห่อ `try/catch (Throwable)` ไว้ เพราะเอฟเฟกต์พังห้ามทำให้การย้ายของพัง

---

## 3. โครงสร้างคลาส (อัปเดตเมื่อมีการเปลี่ยน)

แพ็กเกจฐาน `com.peakkup.pipeplugin`

| คลาส | หน้าที่ |
|---|---|
| `PipePlugin` | bootstrap: โหลด config, สร้าง FoliaLib, สร้าง services, ลงทะเบียน 3 listener |
| `PipeConfig` | อ่าน `config.yml`: `itemsPerCycle`, `maxPipeLength`, `minPulseIntervalMillis`, `effectsEnabled`, `matchMode`, `allowedContainers` (null = ทุก Container); รับ `PipeLang` เพื่อ log warning |
| `PipeLang` | โหลด `lang.yml` (ข้อความ console/log ทั้งหมด เป็นภาษาอังกฤษ); `msg(key, k,v,...)` แทนที่ `{placeholder}`; มี default จาก jar เผื่อ key หาย |
| `MatchMode` | enum `TYPE` (เทียบ Material) / `SIMILAR` (เทียบ NBT เป๊ะด้วย `isSimilar`) — default = SIMILAR |
| `PipeNetwork` | โมเดลท่อ 1 เส้น: input, source, glassMaterial, `pipeBlocks`(Set กระจก), `outputs`(List), `outputsByGlass`(Map), `busy`(AtomicBoolean) |
| `PipeOutput` | 1 จุดปล่อย: `glassBlock`, `piston`, `destContainer` |
| `NetworkDiscovery` | BFS ค้นหา/ตรวจสอบโทโพโลยีจาก sticky piston → คืน `PipeNetwork` (เก็บ output ทุกตัว) |
| `NetworkRegistry` | cache: `byInputPiston` + reverse index `blockToInput` (สำหรับ invalidate) |
| `Directions` | ลำดับทิศเดินท่อ: หน้า→ซ้าย→ขวา→บน→ล่าง (`leftOf`, `ordered`, `orderedWithBack`) |
| `FrameIndex` | **block gate:** `buildAsync` สแกน item frame ทั้งท่อ **ทีละ chunk บน region ของมัน** (Folia-safe) แล้ว chain รวม → ตอบ `passes(block,item,mode)` รายบล็อก; `empty()` ไว้เทสต์ |
| `PipeRouter` | จัดเส้นทางไอเทม 1 ชนิดด้วย BFS เคารพ gate (เช็ค gate ของ input piston ครั้งเดียว + gate ของกระจก/output piston) → คืน `List<PipeOutput>` เรียงตามลำดับความสำคัญ |
| `ItemTransferService` | flow ย้ายของ: `buildAsync` (สแกนเฟรม) → extract → distribute → return แบบ Folia-safe + กันของหาย/ค้าง; `playEffect` เสียง/particle ตอนสำเร็จ |
| `listener/RedstoneTriggerListener` | จับ rising-edge ของ redstone ที่ sticky piston → เช็ค `min-pulse-interval` (per-piston) → สั่ง transfer |
| `listener/PistonGuardListener` | cancel extend/retract ของ piston ที่อยู่ในท่อ (กันดันบล็อก) |
| `listener/NetworkInvalidationListener` | ล้าง cache เมื่อบล็อกในท่อ (หรือเพื่อนบ้าน) ถูกเปลี่ยน |

> **ถ้าเพิ่ม/ลบ/รวมคลาสใด ๆ ในตารางนี้ → แก้ตารางนี้ด้วยเสมอ**

---

## 4. หลักการที่ห้ามทำพัง (Invariants)

1. **ของต้องไม่หายและไม่ดูป (no loss / no dupe)**
   - ดูดออกจากต้นทางแล้วเก็บเป็น "จำนวน + proto" ในหน่วยความจำ (ไม่ใช่ ItemStack จริงลอย ๆ) → ไม่ดูป
   - ของที่ยัดปลายทางไม่ลง **ต้องถูกคืนต้นทางเสมอ** (เฟส return); คืนไม่ได้จริง ๆ ค่อย drop ที่ต้นทาง
   - เฟส return **ต้องรันเสมอ** แม้เฟส insert จะ throw → ใช้ `CompletableFuture.handle()` กลืน exception ก่อนเรียก return
   - ทุก inventory op ห่อ try/catch ไม่ให้ throw ทะลุจนข้ามการคืนของ

2. **ห้ามท่อค้าง (no stuck pipe)**
   - `PipeNetwork.busy` (AtomicBoolean) กัน pulse ซ้อน — `tryAcquire()` ตอนเริ่ม, `release()` ตอนจบ
   - **ทุก path ที่ acquire แล้วต้อง release** รวมถึงตอน exception:
     - `transfer()` acquire แล้วห่อการ schedule ด้วย try/catch + `.exceptionally(release)` (กันกรณี schedule ไม่ติด/ task ไม่ถูกรัน → busy ค้างถาวร)
     - extract ห่อ try/catch แล้ว release; distribute release ใน `whenComplete`

3. **Folia-safety: แตะ inventory เฉพาะบนเธรดของ region ที่เป็นเจ้าของ**
   - extract/return → `runAtLocation(sourceContainer)`
   - insert แต่ละ output → `runAtLocation(destContainer)` ของอันนั้น
   - เชื่อมข้าม region ด้วย `CompletableFuture.thenCompose` (มี happens-before ให้ค่าที่เขียนเห็นข้ามเธรด)
   - `isBlockIndirectlyPowered` / `getNearbyEntities` ก็ต้องอยู่บนเธรด region เช่นกัน

4. **กระจกแต่ละสี = เครือข่ายแยก** — BFS เดินผ่าน Material กระจกเดียวกันเท่านั้น

5. **piston เป็นแค่ marker** — `PistonGuardListener` ยกเลิกการขยับจริงของ piston ในท่อ

---

## 5. Flow การย้ายของ ต่อ 1 pulse

```
RedstoneTriggerListener.onRedstone
  └─(กรองถูก ๆ: เป็นท่อที่ cache ไว้ หรือมีกระจกติด)→ runAtLocation(piston): checkPiston
        └─ rising edge? + ผ่าน min-pulse-interval? → discover/validate → ItemTransferService.transfer(net)
              └─ net.tryAcquire() → FrameIndex.buildAsync (สแกนเฟรมทีละ chunk บน region ของมัน แล้ว chain รวม)
                    └─ whenComplete(frames) → runAtLocation(source): extractAndDistribute(net, frames)
                          ├─ extract: เลือก "ชนิดแรก" (ตามลำดับช่อง) ที่ router.route(net, proto, frames) ไปถึง output ได้
                          │           ดูดสูงสุด itemsPerCycle (ของ stack ไม่ได้ = 1) → playEffect(source) → สร้าง TypeJob เดียว แล้วหยุด
                          └─ distribute: chain CompletableFuture
                                ต่อ job × dest (เรียงความสำคัญ) → runAtLocation(dest): insertStep (priority fill, playEffect เมื่อเข้าจริง)
                                .handle(กลืน error) → runAtLocation(source): returnLeftovers
                                .whenComplete → net.release()
```

> **release ทุก path:** ถ้า `buildAsync` จบแบบ exception → `whenComplete` release; ถ้า schedule extract ไม่ติด → catch release; distribute release ใน `whenComplete`

**กฎ routing (PipeRouter):** เช็ค gate ของ input piston ก่อน (ไม่ผ่าน = ดูดไม่ได้เลย) → BFS จาก input → ใกล้ก่อน,
ที่ทางแยกเลือกซ้ายก่อนขวา (`Directions.ordered`), เข้ากระจก/รับ output ได้เฉพาะที่ `FrameIndex.passes(block,...)` ยอม;
ถ้าซ้ายโดน filter กั้นก็ไปขวา; คืน output เรียงลำดับความสำคัญ

**กฎ filter (FrameIndex — block gate):** frame ที่แปะบล็อก B (support = บล็อกอากาศของ frame + `getAttachedFace()`) = คุมบล็อก B;
ไม่มีเฟรมบน B = ผ่าน; มีเฟรม = ของต้อง match อย่างน้อยหนึ่งอัน (union); เทียบตาม `MatchMode`

---

## 6. Trigger & Cache

- ใช้ `BlockRedstoneEvent` จับ rising-edge **ไม่ใช่** `BlockPistonExtendEvent` (เพราะ piston ที่หันเข้า container ดันไม่ได้ → extend event ไม่ยิง)
- rising-edge ดูจาก `poweredState` map (false→true) + `busy` flag กัน double-fire
- **rate limit:** `lastFire` map (per input piston) บังคับ `min-pulse-interval-ticks` (default 2 = 10 รอบ/วิ); 0 = ปิด — วัดด้วย `System.currentTimeMillis()` (ไม่มี global tick counter ใน Spigot API)
- ตัวกรองราคาถูกใน `onRedstone`: ข้าม sticky piston ที่ไม่ใช่ท่อ cache และไม่มีกระจกติด (กัน schedule งานทุก redstone tick ของ piston ธรรมดา)
- cache invalidation: ฟัง break/place/explode/burn/fade + เพื่อนบ้าน 6 ทิศ → ลบ network ออกจาก registry → ค้นใหม่ pulse ถัดไป
- การเปลี่ยนบล็อกแบบไม่ยิง event (WorldEdit/`/setblock`) จะไม่ invalidate → topology อาจ stale ชั่วคราว (ยอมรับได้: insert ตรวจ container ซ้ำเสมอ)

---

## 7. config.yml

```yaml
items-per-cycle: 32          # จำนวนสูงสุด/รอบ ของชนิดที่ถูกเลือก (>=1); ของ stack ไม่ได้ = 1 ชิ้น/รอบ
max-pipe-length: 64          # จำนวนกระจกสูงสุดที่ BFS ไล่ (>=1)
min-pulse-interval-ticks: 2  # ช่วงขั้นต่ำระหว่างรอบของท่อเดียวกัน (>=0; 20 ticks=1 วิ); 0=ไม่จำกัด
effects: true                # เสียง+particle ที่ต้นทาง/ปลายทางตอนย้ายสำเร็จ; false=เงียบ
filter-match: SIMILAR        # SIMILAR=NBT เป๊ะ (default) | TYPE=เทียบ Material
allowed-containers: all      # all=ทุก Container | หรือ list ของ Material
```

**คอมเมนต์ทั้งใน `config.yml` เป็นภาษาอังกฤษ**

### lang.yml
ข้อความ console/log ทั้งหมด (ภาษาอังกฤษ) อยู่ใน `resources/lang.yml` โหลดผ่าน `PipeLang`
(ปลั๊กอินไม่มีข้อความแชตในเกม มีแต่ log — key: `plugin-enabled`, `config.unknown-container`, `transfer.*` รวม `transfer.frame-scan-failed`)
`{placeholder}` เช่น `{platform}`, `{material}` ถูกแทนที่ตอน runtime

---

## 8. ความปลอดภัย / robustness ที่พิจารณาแล้ว

- **ของหาย/ดูป:** กันด้วยรูปแบบ extract(count)→insert→return + handle()/try-catch (ข้อ 4.1)
- **ท่อค้าง:** release ทุก path รวม exception (ข้อ 4.2)
- **DoS เบา ๆ:** `min-pulse-interval-ticks` จำกัดอัตรารอบต่อท่อ (default 2 ticks); `busy` กันซ้อน; `maxPipeLength` จำกัด BFS; ตัวกรอง onRedstone กันงานเกินจาก piston ธรรมดา
- **NPE world ไม่โหลด:** `FrameIndex.buildAsync`/`scanChunk` (เช็ค world null + `isChunkLoaded`) และ `dropItems` เช็ค world null
- **effect enum ต่างเวอร์ชัน:** `playEffect` ห่อ `try/catch (Throwable)` — เอฟเฟกต์พังไม่กระทบการย้ายของ
- **griefing:** ใครก็แปะ item frame บนท่อคนอื่นได้ (เป็นธรรมชาติของ UI นี้) → ควบคุมด้วยปลั๊กอิน protection ภายนอก (WorldGuard ฯลฯ) — นอกขอบเขตปลั๊กอินนี้

### ข้อจำกัดที่ยอมรับไว้ (Known limitations)
- **Folia cross-region:** ✅ แก้แล้ว — `FrameIndex.buildAsync` สแกนเฟรมทีละ chunk บน region ของ chunk นั้น และการ insert ปลายทางก็แยกตาม region อยู่แล้ว จึงรองรับท่อข้าม region (แลกกับ scheduler hop ต่อ chunk ที่ท่อพาดถึง → ช้าลงเล็กน้อย)
- **pulse สั้นมาก (sub-tick):** เพราะอ่านกำลังไฟใน tick ถัดไป pulse 1 tick จาก observer อาจพลาดบ้าง (trade-off ของ Folia-safety)
- **`poweredState`** เก็บ entry จนกว่า piston จะหาย (เคลียร์เมื่อ checkPiston พบว่าไม่ใช่ sticky piston) — memory leak เล็กน้อยมีขอบเขตจำกัด

---

## 9. Build, test & verify

```bash
.\gradlew.bat test           # รัน unit test (JUnit 5 + Mockito)
.\gradlew.bat shadowJar      # -> build/libs/PipePlugin-1.0.0.jar (FoliaLib relocate อยู่ภายใน)
```
- ตรวจว่า compile ผ่าน = ไม่ได้แตะ API นอก 1.20.1
- ตรวจคลาสใน jar: ต้องมีคลาสตามตารางข้อ 3 ครบ
- **ทดสอบในเกมจริง** (3 platform: Spigot 1.20.1 / Paper ล่าสุด / Folia ล่าสุด) — เครื่องมือนี้ทำได้แค่ compile + unit test + ตรวจโครงสร้าง

### Unit tests (`src/test/java/...`)
testImplementation: spigot-api (เดิม compileOnly), `junit-jupiter`, `mockito-core`
รันบน JDK 24 → ตั้ง `net.bytebuddy.experimental=true` ใน `tasks.test` ให้ ByteBuddy รันบน JVM ที่ยังไม่ถูก whitelist

| ไฟล์ | ครอบคลุม |
|---|---|
| `DirectionsTest` | ตรรกะลำดับทิศ pure: leftOf, ordered, orderedWithBack, การันตี "ซ้ายก่อนขวา" |
| `MatchModeTest` | TYPE/SIMILAR, null, `fromConfig` (mock `ItemStack`) |
| `PipeRouterTest` | BFS routing: เส้นตรง, ไม่มี output, ใกล้ก่อนไกล, ทางแยกซ้ายก่อนขวา, หลาย output ต่อกระจก (ใช้ `GridWorld` + `FrameIndex.empty()`) |
| `GridWorld` | โลกจำลอง (mock Block/World/Directional) เดิน getRelative/getLocation/getBlockAt ได้ — ใช้เทสต์ traversal โดยไม่ต้องรัน server |

> **เพิ่ม test ที่พึ่ง Bukkit จริงทั้ง pipeline (extract/insert/return, filter ด้วย entity จริง) ต้องใช้ MockBukkit หรือ integration test บนเซิร์ฟเวอร์ — ยังไม่ได้ทำ**
> หมายเหตุ: `FrameIndex.empty()` มีไว้ช่วยเทสต์ routing โดยตัด gate ออก — อย่าลบ

---

## 10. Out of scope (ตั้งใจไม่ทำ)

GUI, คำสั่ง admin/visualize, permission ละเอียด, ปรับ rate/ฟิลเตอร์แยกต่อท่อ (rate เป็นค่ารวมใน config)
(ออกแบบเผื่อขยายได้แต่ยังไม่ทำ — ถ้าจะเพิ่ม ให้คงหลักการข้อ 4 และอัปเดตไฟล์นี้)

**อยู่ในขอบเขตแล้ว:** หลาย **output** ต่อท่อ; หลาย **input** (sticky piston หลายตัว) บนกระจกสีเดียวกัน =
คนละ network ที่ใช้ท่อร่วม ต่างคนต่างทำงาน (ตั้งใจ ไม่ reject)
