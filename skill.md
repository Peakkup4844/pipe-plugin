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
| ช่วงเวอร์ชัน | MC **1.20.1 → ล่าสุด (26.2)** ด้วย jar เดียว — **รันจริงยืนยันแล้วบน Spigot 1.20.1 / Paper 1.20.1 / Paper 26.2 / Folia 26.1.2** (ดู §9) |
| API | **Bukkit/Spigot API ล้วน ห้าม NMS / ห้าม API เฉพาะ Paper / ห้าม API เฉพาะเวอร์ชันใหม่** |
| Compile target | `compileOnly spigot-api:1.20.1` + `options.release = 17` → ถ้าเผลอใช้ API ที่ไม่มีใน 1.20.1 จะ compile ไม่ผ่านทันที (เป็น guard rail) |
| Java | bytecode 17 (รันได้บน JVM 17→21+) |
| Folia | ผ่าน **FoliaLib** (shade + relocate → `com.peakkup.pipeplugin.libs.folialib`) |
| ไฟล์ปลั๊กอิน | `plugin.yml` แบบคลาสสิก (ไม่ใช่ `paper-plugin.yml`), `folia-supported: true`, `softdepend: [WildChests]` (แค่บังคับลำดับ enable — ไม่มี dependency จริง ไม่มีปลั๊กอินนั้นก็โหลดได้ปกติ) |

**ห้ามทำ:** เพิ่ม dependency ที่มีเฉพาะ Paper/Folia แบบ compile-time, เรียก method ที่เพิ่งมีในเวอร์ชันใหม่,
ใช้ `PlayerItemFrameChangeEvent` (Paper-only) — เราจึงอ่าน frame แบบ live แทน

**ระวัง enum churn:** ชื่อค่าใน `Particle`/`Sound` เปลี่ยนข้ามเวอร์ชัน (เช่น `BLOCK_CRACK`→`BLOCK`) —
เอฟเฟกต์ใน `ItemTransferService.playEffect` ใช้ค่าที่เสถียร (`Particle.CRIT`, `Sound.BLOCK_DISPENSER_DISPENSE`)
และห่อ `try/catch (Throwable)` ไว้ เพราะเอฟเฟกต์พังห้ามทำให้การย้ายของพัง
(ทั้งสองค่ายังใช้ได้จริงบน 26.2 — ยืนยันด้วยการรัน ไม่ใช่การอ่านเอกสาร)

---

## 3. โครงสร้างคลาส (อัปเดตเมื่อมีการเปลี่ยน)

แพ็กเกจฐาน `com.peakkup.pipeplugin`

| คลาส | หน้าที่ |
|---|---|
| `PipePlugin` | bootstrap: โหลด config, สร้าง FoliaLib, สร้าง services, ลงทะเบียน 3 listener |
| `PipeConfig` | อ่าน `config.yml`: `itemsPerCycle`, `maxPipeLength`, `minPulseIntervalMillis`, `effectsEnabled`, `matchMode`, `skipPluginManagedChests`, **`callInventoryMoveEvent`**, `allowedContainers` (null = ทุก Container; รับทั้ง list และค่าเดี่ยว/คั่นจุลภาค แล้ว **log ค่าที่ใช้จริงตอน enable**) + **blacklist ถาวร `NEVER_ALLOWED`** (ENDER_CHEST — inventory เป็นของผู้เล่นไม่ใช่ของบล็อก) ที่ `allowed-containers` override ไม่ได้ |
| `RegionExecutor` | ห่อ scheduler ที่เดียว: `at(loc, body)` = **รันทันทีถ้า `isOwnedByCurrentRegion(loc)`** ไม่งั้นค่อย `runAtLocation` (คืน `CompletableFuture` เหมือนกันทั้งสองทาง) + `ownsCurrentThread(loc)` — ดู §4.6 ว่าทำไมสำคัญทั้งเรื่องความเร็วและความปลอดภัย |
| `PipeLang` | โหลด `lang.yml` (ข้อความ console/log ทั้งหมด เป็นภาษาอังกฤษ); `msg(key, k,v,...)` แทนที่ `{placeholder}`; ตกไปใช้ค่าจาก jar เมื่อ key หายผ่าน `lookup()` (**ห้ามใช้ `getString(key, key)` — ดู §7**) |
| `MatchMode` | enum `TYPE` (เทียบ Material) / `SIMILAR` (เทียบ NBT เป๊ะด้วย `isSimilar`) — default = SIMILAR; **ใช้ได้เฉพาะกับประตู item frame เท่านั้น ห้ามเอาไปจัดกลุ่มของ (§4.1e)** |
| `PipeNetwork` | โมเดลท่อ 1 เส้น: input, source, glassMaterial, `pipeBlocks`(Set กระจก), `outputs`(List), `outputsByGlass`(Map), `busy`(AtomicBoolean); `sameTopologyAs` เทียบโครงสร้าง, `adoptLockFrom` รับช่วงล็อกตอน re-discover; **`gateBlocks()` / `frameChunks()` แคชแบบ lazy** (คำนวณจากโทโพโลยีล้วน ๆ) ให้ `FrameIndex` ไม่ต้องประกอบเซ็ตใหม่ทุก pulse |
| `PipeOutput` | 1 จุดปล่อย: `glassBlock`, `piston`, `destContainer`, **`destMaterial`** (ชนิดกล่องปลายทางตอนค้นท่อ — ใช้คัดชนิดของได้ตั้งแต่ฝั่งต้นทางโดยไม่ต้องแตะ region อื่น) — เป็น **value object** (equals/hashCode รวม material) เพื่อเทียบโทโพโลยีได้ |
| `NetworkDiscovery` | BFS ค้นหาโทโพโลยีจาก sticky piston → คืน `PipeNetwork` (เก็บ output ทุกตัว); ถูกเรียก **ทุก pulse** |
| `InventoryOps` | งานระดับช่อง: `removeUpTo` (ดูด + **จำช่องที่ดูด**), `restoreToOriginalSlots` (คืนกลับช่องเดิมก่อน), `countMatching` (นับของชนิดหนึ่ง — ใช้ตรวจการอนุรักษ์ของ) — **ทั้งสามเทียบด้วย `isSimilar` เป๊ะเสมอ ไม่รับ `MatchMode`** (§4.1e) |
| `ExternalStorageGuard` | ตรวจกล่องที่ปลั๊กอินอื่นถือของจริงไว้เอง **2 ชั้น**: (1) `isPluginManaged` ถาม API ของ WildChests ผ่าน reflection (normal/linked; **ยกเว้น storage unit ที่วัดได้**) (2) `isForeignInventory` ตาข่ายที่ไม่ผูกกับปลั๊กอินใด — `Inventory` ที่ไม่ใช่คลาสของเซิร์ฟเวอร์ = ปฏิเสธ; และ (3) `reportedCount(loc, proto)` = **จำนวนจริงจากเจ้าของกล่อง** (`StorageChest#getAmount`, `BigInteger`) หรือ `null` ถ้าไม่มีใครรู้ — ทำงานเสมอไม่ขึ้นกับ config. `hooks()` เช็คว่ามีปลั๊กอินอยู่ด้วย PluginManager lookup ธรรมดา**ก่อน**เข้า `synchronized resolve` (เมธอดนี้ถูกเรียกทุกครั้งที่แตะกล่อง — เดิมทุก region thread แย่งล็อกเดียวกันเพื่อได้คำตอบ "ไม่มี" ซ้ำ ๆ) |
| `ContainerAccess` | **จุดเดียว**ที่ตัดสินว่า "ท่อแตะ container นี้ได้ไหม" + คืน `Inventory` ที่ใช้ได้ (allow-list → **บัญชีดำ** → `Container` → guard ชั้น 1 → guard ชั้น 2) — ใช้ร่วมกันทั้งตอน discover และตอนย้ายของ จึงเพี้ยนคนละทางไม่ได้; ถือ **ชั้นที่ 3**: `distrust(loc)`/`isDistrusted(loc)` = บัญชีดำระหว่างรัน (key `world:x,y,z`, `ConcurrentHashMap`) ที่ `ItemTransferService` เติมเมื่อจับได้ว่ากล่องนับของไม่ตรง; **`trueCount(loc, inv, proto)`** = จุดเดียวที่ตอบ "กล่องนี้มีของชนิดนี้กี่ชิ้นจริง ๆ" (ถามเจ้าของก่อน → ตกมา `countMatching`); และกฎ "ปลายทางรับชนิดนี้ได้ไหม" **2 แบบ**: `acceptsType(loc, inv, proto)` (ต้องมี Inventory — คุม shulker ซ้อน shulker + storage unit คนละชนิด) กับ **`canHold(destMaterial, proto)` แบบ static ที่ตัดสินจาก Material ล้วน** ใช้ตอน extract ได้ (§4.1f) |
| `NetworkRegistry` | cache: `byInputPiston` + reverse index **multimap** `blockToInputs` (block → **เซตของ** input piston) → 1 บล็อกอาจถูกหลายท่อใช้ร่วม, invalidate แล้วลบ **ทุกท่อ** ที่ใช้บล็อกนั้น (mutate ผ่าน `compute`/`computeIfPresent` = atomic ต่อ key) |
| `Directions` | ลำดับทิศเดินท่อ: หน้า→ซ้าย→ขวา→บน→ล่าง (`leftOf`, `ordered`, `orderedWithBack`) |
| `FrameIndex` | **block gate:** `buildAsync(regions, net, lang)` สแกน item frame ทั้งท่อ **ทีละ chunk บน region ของมัน** (Folia-safe) แล้ว chain รวม → ตอบ `passes(block,item,mode)` รายบล็อก; รายชื่อ gate block/chunk มาจาก `PipeNetwork` ที่แคชไว้; `empty()` ไว้เทสต์ |
| `PipeRouter` | จัดเส้นทางไอเทม 1 ชนิดด้วย BFS เคารพ gate (เช็ค gate ของ input piston ครั้งเดียว + gate ของกระจก/output piston) → คืน `List<PipeOutput>` เรียงตามลำดับความสำคัญ |
| `ItemTransferService` | flow ย้ายของ: `buildAsync` (สแกนเฟรม) → extract → distribute → return ผ่าน `RegionExecutor` + กันของหาย/ค้าง; `allowedByListeners` ยิง `InventoryMoveItemEvent` ให้ปลั๊กอินป้องกันพื้นที่ยับยั้งได้ (§8d); `playEffect` เสียง/particle ตอนสำเร็จ |
| `listener/RedstoneTriggerListener` | จับ rising-edge ของ redstone ที่ sticky piston → เช็ค `min-pulse-interval` (per-piston) → สั่ง transfer; สถานะทั้งหมดอยู่ใน `Map<Location, PistonState>` **map เดียว** (`queued` / `powered` / `lastFire`) และ `queued` (AtomicBoolean) **ยุบ event ที่ไหลมาเป็นชุดให้เหลืองานเดียวต่อ piston** |
| `listener/PistonGuardListener` | cancel extend/retract ของ pipe piston (กันดันบล็อก) — กัน**เชิงโครงสร้าง**: tracked หรือ (piston หันเข้า container + มีกระจกท่อติด) แม้ cache ว่าง + กัน piston ภายนอกดันบล็อกท่อ; **เช็คกระจกก่อน `getState()` เสมอ** (เมธอดนี้เจอ piston ทุกตัวในเซิร์ฟ) |
| `listener/NetworkInvalidationListener` | ล้าง cache เมื่อบล็อกในท่อ (หรือเพื่อนบ้าน) ถูกเปลี่ยน — ออกก่อนทันทีถ้า `registry.isEmpty()` (ทำงานกับทุกบล็อกที่ถูกวาง/ทุบในเซิร์ฟ) |

> **ถ้าเพิ่ม/ลบ/รวมคลาสใด ๆ ในตารางนี้ → แก้ตารางนี้ด้วยเสมอ**

---

## 4. หลักการที่ห้ามทำพัง (Invariants)

1. **ของต้องไม่หายและไม่ดูป (no loss / no dupe)**
   - ดูดออกจากต้นทางแล้วเก็บเป็น "จำนวน + proto" ในหน่วยความจำ (ไม่ใช่ ItemStack จริงลอย ๆ) → ไม่ดูป
   - ของที่ยัดปลายทางไม่ลง **ต้องถูกคืนต้นทางเสมอ** (เฟส return); คืนไม่ได้จริง ๆ ค่อย drop ที่ต้นทาง
   - เฟส return **ต้องรันเสมอ** แม้เฟส insert จะ throw → ใช้ `CompletableFuture.handle()` กลืน exception ก่อนเรียก return
   - ทุก inventory op ห่อ try/catch ไม่ให้ throw ทะลุจนข้ามการคืนของ
   - **ห้ามแตะกล่องที่ปลั๊กอินอื่นถือของจริงไว้เอง** (`ContainerAccess` + `ExternalStorageGuard`) — เขียนลง
     vanilla inventory ของกล่องพวกนั้น = ของหายตอนกล่องถูกทุบ, ดูดออกมา = อาจเสกของ; เช็คทั้งตอน discover และตอนย้ายของ

1a. **ต้นทางต้องเสียของไปจริง ก่อนจะส่งของออก (verify, don't trust)**
   - หลัง `removeUpTo` ต้องนับต้นทางซ้ำ: `before - after` ต้องเท่ากับจำนวนที่ดูดมา**เป๊ะ**
   - ไม่ตรง = ไม่มีใครยืนยันได้ว่ากล่องเสียของไปจริง → **ยัดกลับทั้งหมด** (`taken.total()`) + `containers.distrust(loc)` + ยกเลิก pulse
   - เป็นด่านที่ **ไม่ผูกกับปลั๊กอินใดเลย** จึงกันได้แม้ guard ทั้งสองชั้นข้างบนจะพลาด
   - อ่านซ้ำในเธรดเดิม tick เดียวกัน → ไม่มีใครแทรกกลางคัน ผลต่างจึงเป็นความจริงเสมอ

   > ### ⚠️ ตัวเลขที่ใช้ตรวจต้องมาจาก `ContainerAccess.trueCount` เท่านั้น — อย่าเรียก `countMatching` ตรง ๆ
   >
   > `trueCount` = ถาม**เจ้าของกล่อง**ก่อน (`ExternalStorageGuard.reportedCount`) แล้วค่อยตกมานับ inventory
   > จำเป็นเพราะ **storage unit ของ WildChests** เก็บจำนวนจริงเป็น `BigInteger` ก้อนเดียวข้างนอก
   > inventory แล้ววาด "สต๊าคโชว์" `min(amount, maxStackSize)` ค้างไว้ในช่องเดิมเสมอ — ดูดออก 32
   > แล้วนับ inventory ซ้ำ **ได้เท่าเดิมทุกครั้ง** ทั้งที่ของหายไปจริง 32 (เคส (ข) ในตารางข้างล่าง)
   > นี่คือบั๊กที่ทำให้ท่อกลืนของหายจริงในเซิร์ฟ แล้วต่อมากลายเป็นขึ้นบัญชีดำกล่องที่ทำงานถูกต้อง
   >
   > ผลต่างต้องคำนวณบน `BigInteger` ด้วย — storage unit เก็บของเกิน `int` ได้ ถ้าย่อเป็น `int`
   > ผลต่างจะกลายเป็น 0 แล้วกล่องที่ปกติดีจะโดนขึ้นบัญชีดำ

   > ### ⛔ ห้ามย้อนกลับไป "คืนเฉพาะจำนวนที่หายไปจริง" — เคยทำแล้วของหายจริงในเซิร์ฟ
   >
   > เวอร์ชันแรกคืนแค่ `max(0, before-after)` โดยให้เหตุผลว่า "คืนเกิน = dupe เสียเอง" **ผิด**
   > เพราะ `lost 0` แยกไม่ออกระหว่างสองกรณีที่ผลลัพธ์ตรงข้ามกันสุดขั้ว:
   >
   > | กล่อง | เกิดอะไรตอน `setItem` | อ่านซ้ำได้ | ถ้าทิ้งของที่ดูดมา |
   > |---|---|---|---|
   > | ก) ไอเทมโชว์เฉย ๆ | ไม่มีผล | ค่าเดิม | ถูกต้อง (ของยังอยู่) |
   > | ข) **หักจริงแต่วาดสต๊าคกลับ** | หักจำนวนจริง | ค่าเดิม | **ของหายทุก pulse** |
   >
   > สองกรณีนี้อ่านจากภายนอกได้ผลเหมือนกันเป๊ะ (ล็อกไว้ด้วยเทสต์คู่ใน `InventoryOpsTest`)
   > `lost 0` จึงแปลว่า **"ไม่รู้"** ไม่ใช่ "ของยังอยู่" — ของอยู่ในมือเราแล้ว หน้าที่เดียวคือยัดกลับ
   >
   > ทางเดียวที่จะ**แยกสองกรณีนี้ออกได้จริง** คือมีคนบอกจำนวนจริงให้ (`reportedCount`)
   > storage unit ของ WildChests = เคส (ข) และตอนนี้ถามตัวเลขจริงได้แล้ว จึงใช้งานได้ตามปกติ
   >
   > สิ่งที่กันความเสี่ยง dupe ของกรณี (ก) ไม่ใช่การคืนน้อยลง แต่คือ **บัญชีดำ** —
   > เกิดได้ครั้งเดียวต่อกล่องต่อการรันเซิร์ฟหนึ่งครั้ง (≤ `items-per-cycle` ชิ้น) แทนที่จะเกิดทุก pulse

1c. **จับได้แล้วต้องเลิกใช้กล่องนั้นถาวร (`ContainerAccess.distrust`)**
   - กล่องที่นับไม่ตรง (ทั้งฝั่งต้นทางและปลายทาง) ถูกใส่ `Set<String>` key = `world:x,y,z` → `inventoryOf` คืน null ตลอด
   - ทำให้ `discover()` มองไม่เห็นกล่องนั้น → ท่อหยุดเอง; ล้างเมื่อรีสตาร์ต/reload เท่านั้น
   - `distrust()` คืน `true` เฉพาะครั้งแรก → ใช้เป็นตัวกันเตือนซ้ำแบบ atomic (ท่อ 2 เส้นคนละ region ก็เตือนครั้งเดียว)
   - key เป็นสตริง **ไม่ใช่ `Location`** เพราะ `Location.equals` รวม yaw/pitch → บัญชีดำจะรั่ว

1d. **ฝั่งปลายทาง: ตรวจได้ แต่ห้ามพยายามกู้ (asymmetry by design)**
   - `insertStep` นับ `before`/`after` รอบ `addItem` ถ้า `gained != inserted` → `distrust(dest)` + เตือน **แต่ไม่ดึงของกลับ**
   - เหตุผล: ของออกจากมือเราไปแล้ว ถ้ามันเก็บไว้จริงแล้วเราคืนต้นทาง = **dupe ทันที**
     ต่างจากฝั่งต้นทางที่ของยังอยู่ในมือ 100% จึงยัดกลับได้อย่างปลอดภัย
   - ถ้า `addItem` **throw** กลางคัน ต้องวัดของจริง (`gainOf`) แทนการเดา ไม่งั้นคืนต้นทางทั้งก้อน = dupe เท่าที่เข้าไปแล้ว

1e. **โหมด filter ห้ามถูกใช้จัดกลุ่ม/สร้างของใหม่ (`filter-match` ≠ วิธีเทียบของ)**
   - ท่อดูดของเป็น "จำนวน + **proto ตัวเดียว**" แล้ว **สร้างสต๊าคใหม่จาก proto** ทั้งตอนส่งออกและตอนคืนของ
   - ถ้าใช้ `MatchMode` ของ filter มาจัดกลุ่ม พอแอดมินตั้ง `filter-match: TYPE` ของที่ stack ได้แต่ NBT
     ต่างกัน (**ลูกศรอาบยา, พลุ**) จะถูกนับเป็นชนิดเดียวกัน → ดูดรวมกัน → ออกมาเป็นชนิดของ **ตัวแรกในกล่อง**
     ทั้งหมด = ผู้เล่นวางของแพงไว้ช่องแรกแล้ว **แปลงของถูกทั้งกล่องเป็นของแพง** (dupe มูลค่าเต็ม ๆ)
   - จึงบังคับว่า `InventoryOps.*`, `alreadySeen`, `trueCount`, `reportedCount`, `rejectsType`
     **เทียบด้วย `isSimilar` เป๊ะเสมอ ไม่รับพารามิเตอร์ `MatchMode`**
   - `MatchMode` เหลือที่ใช้ที่เดียวคือ `FrameIndex.passes` (ประตู item frame) — **อย่าส่งมันลงไปชั้นล่างอีก**

1f. **ปลายทางที่รับของชนิดนั้นไม่ได้ ต้องถูกคัดตั้งแต่ฝั่งต้นทาง ไม่ใช่ตอนยัด**
   - `Inventory#addItem` **ไม่เช็คกฎ `canPlaceItem` ของ vanilla เลย** → ถ้าไม่กันเอง ท่อจะซ้อน shulker
     ใน shulker ได้ทั้งที่ hopper ในเกมทำไม่ได้ (ผู้เล่นใช้ทำ NBT ซ้อนกันจนบวมเพื่อถ่วงเซิร์ฟ/ไคลเอนต์)
   - แต่ถ้ากันแค่ตอน insert อย่างเดียวจะเกิด **ท่อตัน**: extract เลือกชนิดแรกที่ route ได้ → shulker
     ถูกดูดออกมา → ยัดไม่ลง → คืนกลับ → รอบหน้าเลือกชนิดเดิมอีก **ของชนิดอื่นไม่มีวันได้ไป**
     (โยน shulker ใบเดียวลงกล่องต้นทาง = ล็อกท่อสาธารณะได้)
   - จึงคัดที่ `extract` ด้วย `ContainerAccess.canHold(out.destMaterial(), proto)` ซึ่งใช้แค่ **Material
     ที่ `discover()` อ่านไว้แล้ว** → ไม่ต้องแตะ region อื่น; ถ้าไม่เหลือปลายทางเลยให้ `continue` ไปชนิดถัดไป
   - `acceptsType` ตอน insert **ยังอยู่** เป็นด่านสอง (และเป็นด่านเดียวสำหรับ storage unit ที่ต้องถาม API)

1b. **ห้ามทำผังกล่องต้นทางเพี้ยน (no silent reshuffle)**
   - คืนของต้อง **คืนเข้าช่องเดิมที่ดูดมาก่อน** (`InventoryOps.restoreToOriginalSlots`) แล้วค่อย `addItem`
   - ไม่งั้นเวลาปลายทางเต็มแล้วจ่ายไฟค้าง ของจะถูกยุบรวมมาช่องต้น ๆ ทีละ pulse (ผู้เล่นเห็นของขยับเอง)
   - เป้าหมาย: ปลายทางเต็มสนิท = กล่องต้นทางต้องดู "ไม่มีอะไรเกิดขึ้น" เลย

2. **ห้ามท่อค้าง (no stuck pipe)**
   - `PipeNetwork.busy` (AtomicBoolean) กัน pulse ซ้อน — `tryAcquire()` ตอนเริ่ม, `release()` ตอนจบ
   - **ทุก path ที่ acquire แล้วต้อง release** รวมถึงตอน exception:
     - `transfer()` acquire แล้วห่อการ schedule ด้วย try/catch + `.exceptionally(release)` (กันกรณี schedule ไม่ติด/ task ไม่ถูกรัน → busy ค้างถาวร)
     - extract ห่อ try/catch แล้ว release; distribute release ใน `whenComplete`

3. **Folia-safety: แตะ inventory เฉพาะบนเธรดของ region ที่เป็นเจ้าของ**
   - extract/return → `regions.at(sourceContainer, ...)`
   - insert แต่ละ output → `regions.at(destContainer, ...)` ของอันนั้น
   - เชื่อมข้าม region ด้วย `CompletableFuture.thenCompose` (มี happens-before ให้ค่าที่เขียนเห็นข้ามเธรด)
   - `isBlockIndirectlyPowered` / `getNearbyEntities` ก็ต้องอยู่บนเธรด region เช่นกัน

6. **งานที่อยู่บน region ตัวเองอยู่แล้ว ต้องรันทันที ห้าม schedule (`RegionExecutor`)**
   - `foliaLib...runAtLocation` บน Spigot/Paper คือ `BukkitScheduler#runTask` = **เลื่อนไป tick ถัดไปเสมอ**
     แม้จะอยู่บนเธรดหลักอยู่แล้ว (ยืนยันจาก bytecode ของ FoliaLib 0.5.1 ไม่ใช่การเดา)
   - ผลเดิม: 1 pulse กิน ~5 tick (สแกนเฟรม → ดูด → ใส่แต่ละ output → คืนของ อย่างละ tick) ท่อจึง
     **ช้า** (ยิงถี่ ๆ โดน `busy` ตีกลับเป็นส่วนใหญ่) และ **เปิดหน้าต่าง** ให้ hopper/ผู้เล่นแทรกช่องที่เพิ่งถูกดูด
   - `RegionExecutor.at` เช็ค `isOwnedByCurrentRegion(loc)` ก่อน (Spigot/Paper = `isPrimaryThread()`,
     Folia = การเช็ค region จริง) ถ้าใช่ = รัน body ทันทีแล้วคืน future ที่เสร็จแล้ว
   - ได้ทั้งความเร็วและความปลอดภัย: ทั้งรอบเป็น **atomic ภายใน tick เดียว** บน Bukkit/Paper และบน Folia
     สำหรับท่อที่อยู่ region เดียว (เกือบทั้งหมด เพราะท่อยาวไม่เกิน `max-pipe-length`)
   - **ห้ามเปลี่ยนไปเรียก scheduler ตรง ๆ อีก** — เงื่อนไข `isOwnedByCurrentRegion` คือสิ่งเดียวกับที่ Folia
     ต้องการอยู่แล้ว การรันทันทีจึงไม่ได้ลดความปลอดภัยลงเลย

4. **กระจกแต่ละสี = เครือข่ายแยก** — BFS เดินผ่าน Material กระจกเดียวกันเท่านั้น

5. **piston เป็นแค่ marker** — `PistonGuardListener` ยกเลิกการขยับจริงของ pipe piston แบบ**เชิงโครงสร้าง** (ไม่พึ่ง cache) เพราะช่วง cache ว่าง (ก่อน discover / หลัง invalidate) piston ที่ค้าง quasi-power อาจยืดจริงจนดันท่อ/กล่องแตก

---

## 5. Flow การย้ายของ ต่อ 1 pulse

```
RedstoneTriggerListener.onRedstone
  └─(กรองถูก ๆ: เป็นท่อที่ cache ไว้ หรือมีกระจกติด)→ runAtLocation(piston): checkPiston
        └─ rising edge? + ผ่าน min-pulse-interval? → discover() ใหม่ทุกรอบ
              → เทียบ sameTopologyAs(cache): เท่าเดิม=ใช้ตัวเดิม / ต่าง=adoptLockFrom+put
              → ItemTransferService.transfer(net)
              └─ net.tryAcquire() → FrameIndex.buildAsync (สแกนเฟรมทีละ chunk บน region ของมัน แล้ว chain รวม)
                    └─ whenComplete(frames) → regions.at(source): extractAndDistribute(net, frames)
                          ├─ extract: เลือก "ชนิดแรก" (ตามลำดับช่อง) ที่ router.route(net, proto, frames) ไปถึง output ได้
                          │           **แล้วคัด output ที่ canHold(destMaterial, proto) = false ทิ้ง**
                          │           (ไม่เหลือปลายทาง = ข้ามไปชนิดถัดไป ไม่ใช่ดูดออกมาแล้วคืน — §4.1f)
                          │           ดูดสูงสุด itemsPerCycle (ของ stack ไม่ได้ = 1) เทียบชนิดด้วย isSimilar เป๊ะ (§4.1e)
                          │           → **ตรวจการอนุรักษ์: trueCount ก่อน-หลัง ต้องต่างเท่าที่ดูดมา**
                          │             (trueCount = ถามเจ้าของกล่องก่อน แล้วค่อยนับ inventory; BigInteger)
                          │             (ไม่ตรง = ยัดกลับ **ทั้งหมด** + distrust(source) + ยกเลิกรอบ)
                          │           → playEffect(source) → สร้าง TypeJob เดียว แล้วหยุด
                          └─ distribute: chain CompletableFuture
                                ต่อ job × dest (เรียงความสำคัญ) → regions.at(dest): insertStep
                                      (acceptsType → **ยิง InventoryMoveItemEvent (ยกเลิกได้ = ข้าม output นี้)**
                                       → trueCount before → addItem → เชื่อ leftover; ถ้า gained≠inserted = distrust(dest) เฉย ๆ
                                       ไม่ดึงของกลับ; addItem throw = วัดของจริงด้วย gainOf; playEffect เมื่อเข้าจริง)
                                .handle(กลืน error) → regions.at(source): returnLeftovers
                                      (1. คืนเข้า "ช่องเดิม" ก่อน  2. ที่เหลือ addItem  3. ยังไม่ลง drop)
                                .whenComplete → net.release() (+ log ถ้า schedule เฟสคืนของไม่ติด = เคสเดียวที่ของหายได้)
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
- rising-edge ดูจาก `PistonState.powered` (false→true) + `busy` flag กัน double-fire
- **rate limit:** `PistonState.lastFire` (per input piston) บังคับ `min-pulse-interval-ticks` (default 2 = 10 รอบ/วิ); 0 = ปิด — วัดด้วย `System.currentTimeMillis()` (ไม่มี global tick counter ใน Spigot API)
- ตัวกรองราคาถูกใน `onRedstone`: ข้าม sticky piston ที่ไม่ใช่ท่อ cache และไม่มีกระจกติด (กัน schedule งานทุก redstone tick ของ piston ธรรมดา)
- **ยุบงานซ้ำด้วย `PistonState.queued`:** redstone หนึ่งครั้งยิง `BlockRedstoneEvent` ออกมาเป็นชุด (dust ทั้งเส้น
  อัปเดตทีละก้อน ทั้งขาขึ้นและขาลง) แต่ทุก event ถามคำถามเดียวกัน — "ตอนงานรัน piston มีไฟหรือยัง" — ซึ่ง
  อ่านสดตอนงานรันอยู่แล้ว จึง `compareAndSet(false,true)` ให้เหลืองานเดียวต่อ piston ต่อรอบ
  **เทียบเท่าของเดิมทุกประการ** เพราะงานถูกเลื่อนไป tick ถัดไปอยู่ดี งานที่ถูกยุบจึงจะอ่านค่าเดียวกันเป๊ะ
  (ถ้า schedule ไม่ติดต้อง `queued.set(false)` คืน ไม่งั้น piston ตัวนั้นตายถาวร)
- สถานะทั้งสามอย่างอยู่ใน **map เดียว** (`pistons`) → ค้นครั้งเดียวต่อ event และลบทิ้งพร้อมกันเมื่อบล็อกไม่ใช่ sticky piston แล้ว

### ⚠️ cache **ไม่ใช่** แหล่งความจริงของโทโพโลยี (สำคัญ — อย่าย้อนกลับ)

`checkPiston` เรียก `discovery.discover()` **ทุก pulse** แล้วค่อยเทียบกับ cache:
- เหมือนเดิม (`sameTopologyAs`) → ใช้ object เดิม (คงล็อก `busy`, ไม่ต้องรื้อ reverse index)
- ต่างไป → `adoptLockFrom(cached)` แล้ว `registry.put(fresh)`

เหตุผล: การ invalidate ด้วย event **ครอบไม่ครบโดยธรรมชาติ** —
1. **WorldEdit / `/fill` / `/setblock` / ปลั๊กอินอื่น** ไม่ยิง block event → ท่อที่ถูกลบยัง "ทำงาน" ต่อ
2. **ลำดับการวางที่ invalidate ไม่โดน:** วาง output piston ก่อน (ตอนนั้นยังไม่ใช่ output เพราะไม่มีกล่อง จึงไม่ถูก track)
   แล้วค่อยวางกล่องปลายทาง → ตอนวางกล่อง เพื่อนบ้านของมันคือ piston ที่ยังไม่ถูก track → **ไม่มีอะไรถูก invalidate**
   → output ใหม่ "ล่องหน" จนกว่าจะไปทุบบล็อกอื่นให้ cache ล้าง

ราคาที่จ่าย: BFS ≤ `max-pipe-length` (~64 บล็อก × 6 ทิศ = getType ไม่กี่ร้อยครั้ง) ต่อ pulse ที่ผ่าน rate limit — ถูกกว่าความถูกต้องมาก
`isStillValid` เดิมถูก **ลบทิ้ง** แล้ว (มันตรวจแค่ endpoint จึงจับเคส 1/2 ไม่ได้)

cache ยังมีอยู่เพื่อ: ลดการรื้อ reverse index, และให้ `PistonGuardListener` ใช้ `isTracked`
`NetworkInvalidationListener` ยังเก็บไว้ (ล้าง cache เร็วขึ้น = guard แม่นขึ้น) แต่ **ความถูกต้องไม่ขึ้นกับมันแล้ว**

---

## 7. config.yml

```yaml
items-per-cycle: 32          # จำนวนสูงสุด/รอบ ของชนิดที่ถูกเลือก (>=1); ของ stack ไม่ได้ = 1 ชิ้น/รอบ
max-pipe-length: 64          # จำนวนกระจกสูงสุดที่ BFS ไล่ (>=1)
min-pulse-interval-ticks: 2  # ช่วงขั้นต่ำระหว่างรอบของท่อเดียวกัน (>=0; 20 ticks=1 วิ); 0=ไม่จำกัด
effects: true                # เสียง+particle ที่ต้นทาง/ปลายทางตอนย้ายสำเร็จ; false=เงียบ
skip-plugin-managed-chests: true  # ข้ามกล่องของปลั๊กอิน storage (คุมชั้น 1 และ 2 เท่านั้น, ดู §8b); false=เสี่ยงของหาย/dupe
call-inventory-move-event: true   # ยิง InventoryMoveItemEvent ทุกครั้งที่ยัดของ → ปลั๊กอินป้องกันพื้นที่ยับยั้งได้ (ดู §8d)
filter-match: SIMILAR        # SIMILAR=NBT เป๊ะ (default) | TYPE=เทียบ Material — **มีผลกับ item frame เท่านั้น** (§4.1e)
allowed-containers: all      # all | ค่าเดี่ยว/คั่นจุลภาค | หรือ YAML list ของ Material
```

> `allowed-containers` กรองด้วย **Material** เท่านั้น จึงแยก "chest ธรรมดา" กับ "chest ของ WildChests"
> ไม่ได้ (มันเป็น `CHEST` เหมือนกัน) — นั่นคือเหตุผลที่ต้องมี `skip-plugin-managed-chests` แยกต่างหาก
> ส่วน `ENDER_CHEST` ถูกปฏิเสธเสมอแม้ใส่ไว้ใน list (ดู `PipeConfig.NEVER_ALLOWED`)
>
> ### ⚠️ `allowed-containers` เคยพังแบบเงียบ 2 ทาง — อย่าทำให้กลับมาอีก
> เจอจากรายงานจริง ("ใส่แค่ CHEST แต่ shulker ยังทำงาน") แล้วทำซ้ำได้บนเซิร์ฟจริงทั้งคู่:
> 1. **key ซ้ำใน YAML** — ไฟล์เดิมมีตัวอย่างที่ comment ว่า `# allowed-containers:` อยู่**เหนือ**
>    บรรทัดจริง พอแอดมินเอา `#` ออกก็ได้ 2 key แล้ว **SnakeYAML เก็บแค่ตัวท้าย** → กลายเป็น `all`
>    ปลั๊กอิน**ตรวจเองไม่ได้** เพราะความซ้ำหายไปก่อนถึง `FileConfiguration` แล้ว
>    → แก้ที่ตัวไฟล์: ตัวอย่างในคอมเมนต์ต้องย่อหน้าลึกจนไม่กลายเป็น key ได้ + เขียนเตือนไว้ตรงนั้น
> 2. **ค่าเดี่ยว `allowed-containers: CHEST`** — เดิมโค้ดตี "ไม่ใช่ List = all" เงียบ ๆ
>    → ตอนนี้ค่าเดี่ยวถูกแยกด้วย `[,\s]+` เป็นรายการ; มีแต่ `all` (ไม่สนตัวพิมพ์) ที่แปลว่าทุกอย่าง
>
> **ตัวจับที่ครอบทุกกรณี = log ค่าที่ใช้จริงทุกครั้งที่ enable** (`config.allowed-containers`
> → `Allowed containers: ...`) **ห้ามเอาบรรทัดนี้ออก** มันคือทางเดียวที่แอดมินจะรู้ว่า key ซ้ำ
>
> อีกจุดที่จงใจ: **list ที่อ่านชื่อไม่ออกสักตัวต้อง "ปิดทุกกล่อง" ไม่ใช่ "เปิดทุกกล่อง"**
> (เดิม `set.isEmpty() ? null : set` = เปิดหมด ซึ่งตรงข้ามกับเจตนาของคนที่อุตส่าห์เขียน list)
> และชื่อที่ไม่ใช่บล็อก (เช่น `DIAMOND`) ถูกเตือนแล้วข้าม
>
> **`skip-plugin-managed-chests` ปิดชั้นที่ 3 ไม่ได้** และ **ไม่ปิด `reportedCount`** ด้วย —
> สวิตช์นี้คุมแค่ "จะปฏิเสธกล่องบนหน้าตาไหม" ส่วนความถูกต้องของการนับต้องถูกเสมอ
> **storage unit ของ WildChests ใช้งานได้ทั้งสองค่า** เพราะวัดของได้เป๊ะ (ดู §8b)

**คอมเมนต์ทั้งใน `config.yml` เป็นภาษาอังกฤษ**

### lang.yml
ข้อความ console/log ทั้งหมด (ภาษาอังกฤษ) อยู่ใน `resources/lang.yml` โหลดผ่าน `PipeLang`
(ปลั๊กอินไม่มีข้อความแชตในเกม มีแต่ log — key: `plugin-enabled`, `config.unknown-container`,
`config.allowed-containers`,
`external-storage.*` (`hooked` / `hook-failed` / `foreign-inventory`), `transfer.*` รวม `frame-scan-failed`,
**`source-not-conserved`** / **`dest-not-conserved`** (ชั้นที่ 3 — มี `{inv}` = ชื่อคลาส inventory ไว้ระบุตัวปลั๊กอิน)
และ **`return-schedule-failed`** (เคสเดียวที่ของหายได้ ต้องไม่เงียบ))
`{placeholder}` เช่น `{platform}`, `{material}` ถูกแทนที่ตอน runtime

> ### ⚠️ อ่านข้อความต้องใช้ `getString(key)` เท่านั้น — **ห้าม** `getString(key, key)`
> `PipeLang` ตั้ง `setDefaults()` จาก `lang.yml` ที่ฝังใน jar ไว้ เพื่อให้ key ที่เพิ่มในเวอร์ชันใหม่
> ยังอ่านออกบนเซิร์ฟที่มี `lang.yml` เก่าอยู่ (เราไม่เขียนทับไฟล์ของแอดมิน) **แต่ Bukkit จะข้าม
> defaults ทิ้งทันทีถ้าส่ง default มาเอง** — `MemorySection.get(path, def)` อ่านจาก map ของไฟล์ตรง ๆ
> แล้วคืน `def` เลย มีแต่ overload ที่ไม่ส่ง default ที่วิ่งผ่าน `getDefault(path)`
>
> ผลคือ fallback ที่เขียนไว้**ไม่เคยทำงานเลย** และ console พ่นชื่อ key ดิบ (`config.allowed-containers`)
> ออกมาแทนข้อความ — เจอตอนรันบนเซิร์ฟจริง ไม่ใช่จากการอ่านโค้ด ตอนนี้รวมไว้ที่ `PipeLang.lookup()`
> จุดเดียว และมี `PipeLangTest` คุมไว้ (รวมข้อที่เช็คว่า key ทุกตัวที่โค้ดเรียกมีอยู่จริงในไฟล์)

---

## 8. ความปลอดภัย / robustness ที่พิจารณาแล้ว

- **ของหาย/ดูป:** กันด้วยรูปแบบ extract(count)→insert→return + handle()/try-catch (ข้อ 4.1)
- **ท่อค้าง:** release ทุก path รวม exception (ข้อ 4.2)
- **DoS เบา ๆ:** `min-pulse-interval-ticks` จำกัดอัตรารอบต่อท่อ (default 2 ticks); `busy` กันซ้อน; `maxPipeLength` จำกัด BFS; ตัวกรอง onRedstone กันงานเกินจาก piston ธรรมดา
- **NPE world ไม่โหลด:** `FrameIndex.buildAsync`/`scanChunk` (เช็ค world null + `isChunkLoaded`) และ `dropItems` เช็ค world null
- **effect enum ต่างเวอร์ชัน:** `playEffect` ห่อ `try/catch (Throwable)` — เอฟเฟกต์พังไม่กระทบการย้ายของ
- **griefing:** ใครก็แปะ item frame บนท่อคนอื่นได้ (เป็นธรรมชาติของ UI นี้) → ควบคุมด้วยปลั๊กอิน protection ภายนอก (WorldGuard ฯลฯ)
- **ขโมยของข้ามเขต:** กันด้วยการยิง `InventoryMoveItemEvent` (ดู §8d) — เดิม**ไม่มีอะไรกันเลย**
- **shulker ซ้อน shulker:** `addItem` ไม่เช็ค `canPlaceItem` → กันเองใน `ContainerAccess` ทั้งตอน extract และ insert (§4.1f)
- **ender chest:** อยู่ใน `PipeConfig.NEVER_ALLOWED` ถาวร — inventory เป็นของ "ผู้เล่นที่เปิด" ไม่ใช่ของบล็อก
  ปัจจุบัน Bukkit ไม่ได้ทำให้มันเป็น `Container` อยู่แล้ว แต่กันไว้เผื่อเซิร์ฟเวอร์รุ่นใหม่เปลี่ยน

### 8b. กล่องจากปลั๊กอิน storage (WildChests ฯลฯ) — ป้องกัน 3 ชั้น

**ทำไมอันตราย** (ตรวจจาก `WildChests-2026.2.jar` จริงด้วย `javap`):
- บล็อกเป็น `Material.CHEST` ปกติ → `getState() instanceof Container` เป็นจริง → ผ่านด่านธรรมดาทุกด่าน
- แต่ของจริงอยู่ใน `WChest.getWildContents()` / `WStorageChest.amount` (เป็น `BigInteger`!) ผ่าน `CraftWildInventory`
  ซึ่ง**ไม่ใช่** tile entity ของบล็อก → เขียนลง vanilla inventory = ผู้เล่นมองไม่เห็นและหายตอนทุบ
- storage unit นับของเป็น `BigInteger` และมี `getItemStack()` สำหรับ "ไอเทมที่โชว์" → ถ้าดูดไอเทมโชว์ออกมาได้
  โดยที่ตัวเลขจริงไม่ลด = **dupe ไม่จำกัด**

**ชั้นที่ 1 — ถาม API ตรง ๆ** (`ExternalStorageGuard.isPluginManaged`)
`WildChestsAPI.getChest / getLinkedChest / getStorageChest(Location)` — **static ทั้งสามตัว**
- แต่ละตัว**กรองตามชนิด**: `getChest`→`RegularChest`, `getLinkedChest`→`LinkedChest`, `getStorageChest`→`StorageChest`
  ภายในเป็น `Class.cast()` + `catch ClassCastException → null` **จึงต้องเรียกครบทั้งสามตัว** ตัวเดียวไม่ครอบ
  (`StorageChest extends RegularChest` → `getChest` คืน storage unit ให้ด้วย แต่อีกสองตัวยังจำเป็น)
- key ภายในคือ `BlockPosition(worldName, blockX, blockY, blockZ)` → `block.getLocation()` ตรงกันพอดี
- เก็บ**ทั้ง** static และ instance (`getInstance().getChestsManager()`) ที่หาเจอ แล้วถามทุกตัว (union) ไม่หยุดที่อันแรก
- โหลดคลาสผ่าน **classloader ของ WildChests เอง** (เซิร์ฟเวอร์รุ่นใหม่แยก classloader ระหว่างปลั๊กอิน)
- `plugin.yml` มี `softdepend: [WildChests]` + resolve ซ้ำแบบ lazy → ลำดับ enable ไม่มีผล
- **ยกเว้น storage unit**: ถ้ากล่องเป็น `StorageChest` และ resolve `getItemStack`/`getAmount` ได้ → คืน `false`
  (= ให้ท่อใช้ได้) เพราะวัดของได้เป๊ะแล้ว ดู "รองรับ storage unit" ข้างล่าง

> ⚠️ **บทเรียน:** เวอร์ชันแรกเรียก `WildChestsAPI.getChestsManager()` ซึ่ง **ไม่มีอยู่จริง** →
> `NoSuchMethodException` → hook พังเงียบ ๆ → กล่องไม่ถูกป้องกันเลยทั้งที่ config เปิดอยู่
> **นี่คือเหตุผลที่ต้องมีชั้น 2 และ 3 — ห้ามพึ่ง API ของคนอื่นเป็นด่านเดียว**

**ชั้นที่ 2 — ไม่ผูกกับปลั๊กอินใด** (`ExternalStorageGuard.isForeignInventory`)
`Container#getInventory()` ของ vanilla ได้คลาสจาก `org.bukkit.craftbukkit.` เสมอ (double chest = `CraftInventoryDoubleChest`)
ถ้าได้อย่างอื่น = ปลั๊กอินสวม storage ของตัวเองไว้ → ไม่แตะ + log ชื่อคลาสนั้นครั้งเดียว

> ⚠️ **ชั้นนี้จับ "storage unit" ไม่ได้ ถ้าปลั๊กอินไม่สวม Inventory ของตัวเอง** — รูปแบบที่เจอจริงคือ
> บล็อก CHEST + inventory ของ**เซิร์ฟเวอร์เอง** ที่ถูกใช้เป็น "ไอเทมโชว์" ส่วนจำนวนจริงอยู่ในตัวนับข้างหลัง
> ทั้งชั้น 1 (ถ้า API ตอบว่าไม่รู้จัก) และชั้น 2 มองไม่เห็นเลย → **ชั้น 3 คือด่านสุดท้ายจริง ๆ**

**ชั้นที่ 3 — ตรวจผลลัพธ์จริง + ขึ้นบัญชีดำ** (invariant 1a / 1c / 1d ข้างบน)
นับของก่อน/หลังทุกรอบทั้งสองฝั่ง ไม่ตรง = `ContainerAccess.distrust(loc)` ถาวร กันแม้ทั้งสองชั้นแรกพลาด

#### รองรับ storage unit ได้จริง (ไม่ใช่แค่ปฏิเสธ) — `ExternalStorageGuard.reportedCount`

ตรวจจาก `WStorageChest` ด้วย `javap` แล้ว ตัวเลขทั้งหมดยืนยันจาก bytecode ไม่ใช่การเดา:

| สิ่งที่ทำ | WildChests ทำอะไรจริง |
|---|---|
| `setItem(1, สต๊าค n ชิ้น)` เมื่อโชว์อยู่ `d = min(amount, maxStackSize)` | `setAmount(amount - (d - n))` แล้ว `update()` **วาดสต๊าค `d` กลับทันที** |
| `setItem(1, null)` | `setAmount(amount - d)` (หรือเคลียร์ทั้งใบถ้า `amount == 1`) |
| `setItem(ช่องอื่น, สต๊าค n ชิ้น)` | `setAmount(amount + n)` ← ทางที่ `addItem` ใช้ตอนใส่ของเข้า |
| นับ inventory ซ้ำ | **ได้ `d` เท่าเดิมเสมอ** ไม่ว่าของจะเข้าออกเท่าไหร่ |

- inventory มี `INVENTORY_SIZE = 5` ช่อง สต๊าคโชว์อยู่ช่อง index 1 เสมอ ที่เหลือว่าง → `addItem` มีที่ลงเสมอ
- `getAmount()` (และ `getExactAmount()` ที่ delegate ให้) คือ **ความจริงเพียงหนึ่งเดียว** เป็น `BigInteger`
- `reportedCount` อ่านจาก **interface ของ API** (`api.objects.chests.StorageChest`) ไม่ใช่คลาส impl
  และเช็ค return type ก่อนใช้ (`BigInteger` / `ItemStack`) — API เปลี่ยนชนิดเมื่อไหร่ = คืน null กลับไปนับ inventory
- ทำงาน **ตลอดเวลา ไม่ขึ้นกับ `skip-plugin-managed-chests`** เพราะเป็นเรื่อง "นับให้ถูก" ไม่ใช่นโยบาย
  (ตอนแอดมินปิดสวิตช์คือตอนที่จำเป็นที่สุด)
- ชนิดอื่น (chest ปกติ / linked) → คืน `null` เพราะของอยู่ใน page ที่เป็น Bukkit inventory จริง ๆ อยู่แล้ว
- **`rejectsType` / `ContainerAccess.acceptsType`** — storage unit เก็บได้ชนิดเดียว ถ้ายัดผิดชนิด
  WildChests **โยนของทิ้งพื้น** แต่ `addItem` ยังบอกว่ารับครบ → ถ้าไม่ถามก่อน ด่านฝั่งปลายทางจะ
  ขึ้นบัญชีดำกล่องที่ทำงานถูกต้อง `insertStep` จึงถามก่อนเสมอแล้วข้ามไป output ถัดไป
  **เมื่อไม่รู้ต้องตอบ "รับได้" เสมอ** ไม่งั้นจะไปปิดกั้นกล่องธรรมดา

> **ถ้าจะรองรับปลั๊กอิน storage เพิ่ม:** เพิ่ม lookup ใน `ExternalStorageGuard` ที่เดียว —
> `isPluginManaged` ถ้าแค่อยากปฏิเสธ, `reportedCount` ถ้าอยากให้ท่อ**ใช้งานมันได้จริง**
> ชื่อคลาส inventory ถูกใส่ไว้ในข้อความเตือนของชั้น 3 แล้ว (`{inv}`) เพื่อระบุตัวปลั๊กอินได้เร็ว

### 8d. ปลั๊กอินป้องกันพื้นที่ต้อง "เห็น" ท่อ (`InventoryMoveItemEvent`)

**ช่องโหว่เดิม:** ท่อดูดของจากกล่องที่ sticky piston จ่อ โดยไม่ยิง event ใด ๆ เลย → **ไม่มีปลั๊กอินไหน
บนเซิร์ฟรู้ว่าของขยับ** ผู้เล่นจึงเอา sticky piston ไปจ่อกล่องในเขตของคนอื่น (วาง piston นอกเขต หัวชี้เข้าใน)
แล้วดูดของออกมาได้ทั้งกล่อง WorldGuard/GriefPrevention/Towny/LWC กันไม่ได้ CoreProtect ก็ไม่มีล็อก
— เป็นช่องเดียวกับ "hopper ลอดเข้าเขต" ซึ่งเซิร์ฟทั่วไปกันไว้ผ่านอีเวนต์นี้อยู่แล้ว

**ที่แก้:** `ItemTransferService.allowedByListeners` ยิง `InventoryMoveItemEvent(source, item, dest, true)`
ก่อนยัดของทุกครั้ง ถูก cancel = ข้าม output นั้น (ของกลับต้นทางตามเฟส return ปกติ **ไม่หาย**)

- ยิง **หนึ่งครั้งต่อ output ด้วยจำนวนทั้งก้อน** ไม่ใช่ทีละชิ้น — ปลั๊กอินป้องกันดูตำแหน่งเป็นหลัก ไม่ได้ดูจำนวน
- listener โยน exception = **ปล่อยผ่าน** (log ไว้) ห้ามให้ปลั๊กอินคนอื่นพังแล้วทำให้ของค้างในท่อ
- **Folia:** ยิงเฉพาะเมื่อ `regions.ownsCurrentThread(sourceContainer)` เพราะ listener อาจอ่าน
  `event.getSource()` ซึ่งเป็น inventory ของอีก region = ผิดกฎเธรด ท่อข้าม region จริง ๆ จึงข้ามการยิงไป
- ปิดได้ด้วย `call-inventory-move-event: false` (เผื่อปลั๊กอิน anti-lag ที่ cancel ทุก hopper move)

> **เทสจริงแล้ว** ด้วยปลั๊กอินตัวแทน (`Watch.jar` ใน scratchpad) ที่ log ทุกอีเวนต์ + cancel เมื่อมีไฟล์
> `plugins/Watch/cancel`: เห็นบรรทัด `WATCH-MOVE 0,100,0 -> 6,100,0 DIRT x32` ครบทุก pulse และเมื่อ cancel
> **ของไม่ขยับเลยและไม่หายสักชิ้น** (Spigot 1.20.1 / Paper 1.20.1 / Paper 26.2)

### 8c. ตารางทางที่ผู้เล่นจะลอง dupe (ไล่ทีละทาง)

| # | สิ่งที่ผู้เล่นทำ | ทำไมไม่ dupe |
|---|---|---|
| 1 | กด pulse รัว / redstone clock | `busy` (AtomicBoolean) กันรอบซ้อน + `min-pulse-interval-ticks` ต่อท่อ |
| 2 | ทุบกล่อง **ต้นทาง** กลางรอบ (หลังดูด ก่อนส่ง) | ของอยู่ในหน่วยความจำแล้ว → insert ปลายทางตามปกติ; ถ้าเหลือแล้วต้นทางหาย → drop ที่ตำแหน่งเดิม |
| 3 | ทุบกล่อง **ปลายทาง** กลางรอบ | `inventoryAt` คืน null → ของทั้งก้อนกลับต้นทาง |
| 4 | เปลี่ยนกล่องปลายทางเป็น WildChest กลางรอบ | guard เช็คตอน insert (ไม่ใช่แค่ตอน discover) → คืนต้นทาง |
| 5 | 2 ท่อดูดกล่องเดียวกันพร้อมกัน | คนละ network แต่ extract ทั้งคู่รันบน region thread ของกล่องนั้น = เรียงคิวกัน ต่างคนต่างเห็นของที่เหลือจริง |
| 6 | 2 ท่อยิงเข้ากล่องปลายทางเดียวกัน | insert รันบน region thread ของปลายทาง = เรียงคิวกัน `addItem` เห็นสภาพจริง |
| 7 | ต่อท่อวนกลับเข้าต้นทางตัวเอง | ดูดแล้วใส่คืนที่เดิม = จำนวนคงที่ (แค่เปลืองรอบ) |
| 8 | ใช้ **storage unit** เป็นต้นทาง (ดูดไอเทมโชว์) | ชั้น 1 (API) + ชั้น 2 (inventory ไม่ใช่ของเซิร์ฟเวอร์) + ชั้น 3 (นับก่อน/หลังไม่ลด → ยัดกลับ + ขึ้นบัญชีดำถาวร) |
| 8b | **storage unit ของปลั๊กอินที่เราไม่รู้จัก** (บล็อก CHEST + inventory ของเซิร์ฟเวอร์เอง) | ชั้น 1/2 มองไม่เห็น → เหลือชั้น 3 ล้วน ๆ: เสียหายได้ **≤ 1 รอบต่อกล่องต่อการรันหนึ่งครั้ง** แล้วไม่แตะอีกเลย |
| 8c | ปลายทางเป็น storage unit (ยัดของเข้าไปแล้วหาย) | นับ before/after รอบ `addItem` → `gained ≠ inserted` = ขึ้นบัญชีดำ (ไม่ดึงของกลับ เพราะจะ dupe — ดู 1d) |
| 9 | ใช้ **linked chest** (หลายกล่องแชร์ของกัน) | ชั้น 1 เช็ค `getLinkedChest` ด้วย |
| 10 | ใช้ ender chest (ทุกใบในโลก = ใบเดียวกัน) | `NEVER_ALLOWED` ปฏิเสธถาวร |
| 11 | ใช้ hopper คั่นเพื่อให้ vanilla ขยับของด้วย | hopper ก็ conserve ของอยู่แล้ว; ท่อไม่เคย "สร้าง" ของ มีแต่ย้าย |
| 12 | สลับ item frame กลางรอบ | filter อ่านตอน route เท่านั้น; เปลี่ยนทีหลังอย่างมากทำให้ของกลับต้นทาง |
| 13 | ทำให้ insert throw (ปลั๊กอินอื่น cancel) | `insertStep` จับ exception แล้วคง `remaining` ไว้ → เฟส return ส่งกลับต้นทาง |
| 14 | ลบท่อด้วย `/fill` ระหว่างจ่ายไฟ | discover ใหม่ทุก pulse → รอบถัดไปไม่เจอท่อ = ไม่ทำงาน |
| 15 | ทำให้ pipeline พังกลางทาง (chunk unload ฯลฯ) | ทุก path release `busy` และของที่ดูดมาแล้วถูกคืน/drop เสมอ |
| 16 | ตั้ง `filter-match: TYPE` แล้ววาง **ของแพงที่ NBT ต่างกันแต่ stack ได้** ไว้ช่องแรก (ลูกศรอาบยา/พลุ) | การจัดกลุ่มและการสร้างของคืนใช้ `isSimilar` เป๊ะเสมอ โหมด filter แตะไม่ถึง (§4.1e + `InventoryOpsTest`) |
| 17 | ให้ท่อยัด **shulker ลงใน shulker** เพื่อทำ NBT ซ้อนกันจนบวม | `canHold` คัดตั้งแต่ extract + `acceptsType` กันตอน insert (§4.1f) — และไม่ทำให้ท่อตันด้วย |
| 18 | จ่อ sticky piston เข้ากล่องในเขตคนอื่นแล้วดูดของ | ยิง `InventoryMoveItemEvent` ให้ปลั๊กอินป้องกันพื้นที่ยับยั้ง (§8d) — cancel แล้วของกลับต้นทางครบ |

> **หลักคิดร่วม:** ของถูกดูดออกมาเป็น "จำนวน + proto" ครั้งเดียวต่อรอบ แล้วต้องจบที่ **insert / คืนต้นทาง / drop**
> อย่างใดอย่างหนึ่งเสมอ ไม่มี path ไหนสร้างของเพิ่ม — และตั้งแต่ invariant 1a ก็ไม่เชื่อแม้แต่ตัวเลขจาก inventory เอง
>
> **กับกล่องที่เราไม่เข้าใจ เป้าหมายไม่ใช่ "ทำให้ถูก" แต่คือ "จำกัดความเสียหายให้เกิดได้ครั้งเดียว"**
> เพราะเมื่อ inventory โกหก จะไม่มีทางเลือกไหนที่ปลอดภัย 100% — มีแต่ทางที่ขอบเขตจำกัด (บัญชีดำ)

### ข้อจำกัดที่ยอมรับไว้ (Known limitations)
- **Folia cross-region — ส่วน inventory/entity: ✅ แก้แล้ว** — `FrameIndex.buildAsync` สแกนเฟรมทีละ chunk บน region ของ chunk นั้น และการ insert ปลายทางก็แยกตาม region อยู่แล้ว จึงรองรับท่อข้าม region (แลกกับ scheduler hop ต่อ chunk ที่ท่อพาดถึง → ช้าลงเล็กน้อย)
- **Folia cross-region — ส่วน topology (discover/route): best-effort** — `NetworkDiscovery.discover` / `PipeRouter.route` อ่าน **ชนิดบล็อก** เพื่อนบ้านบนเธรดของ region ที่ trigger เท่านั้น ถ้าท่อพาดข้าม region การอ่านฝั่งข้าม region อาจ throw/อ่านคลาดบน Folia → **worst case = ข้าม pulse นั้นแล้ว re-discover รอบหน้า ไม่มีทาง dupe/หาย** (ส่วนที่แตะ inventory ปลอดภัยเต็มแล้ว) ท่อ ≤ `maxPipeLength` เกือบทั้งหมดอยู่ region เดียวจึงไม่ค่อยเจอ — ดู §9d ใน TESTING.md
- **กล่องที่ inventory "โกหก" ซึ่งชั้น 1/2 จับไม่ได้:** ชั้น 3 จับได้ก็ต่อเมื่อ**ลงมือไปแล้ว** จึงยอมรับความเสียหาย
  **≤ 1 รอบ (`items-per-cycle`) ต่อกล่องต่อการรันเซิร์ฟหนึ่งครั้ง** — ฝั่งต้นทางยัดของกลับได้ (เสี่ยงแค่ dupe ในเคสไอเทมโชว์ล้วน)
  ฝั่งปลายทางกู้ไม่ได้ (จะ dupe) จึงยอมเสียของรอบนั้น หลังจากนั้นกล่องถูกขึ้นบัญชีดำ ไม่แตะอีกเลย
- **storage unit ที่ของเต็ม `maxAmount`:** WildChests จะ **drop ของลงพื้น** แทนการรับเข้า (`ItemUtils.dropItem`)
  แต่ `addItem` ยังรายงานว่ารับครบ → ด่านฝั่งปลายทางเห็น `gained < inserted` → ขึ้นบัญชีดำกล่องนั้น
  **ของไม่หาย** (อยู่บนพื้น) แต่ท่อจะเลิกใช้กล่องนั้นถาวรทั้งที่เป็นแค่สภาวะชั่วคราว — ยอมรับไว้เพราะ
  `maxAmount` ปกติสูงมาก และการเดาว่า "คงเต็มมั้ง" แล้วปล่อยผ่านจะทำให้ด่านนี้ไร้ความหมาย
  (เคส "ใส่ผิดชนิด" ซึ่งเกิดบ่อยกว่ามาก ถูกกันไว้แล้วด้วย `acceptsType`)
- **บัญชีดำอยู่ในหน่วยความจำ** — หายเมื่อรีสตาร์ต/reload (กล่องจะถูกจับใหม่ในรอบแรกหลังรีสตาร์ต)
  และไม่ถูกล้างถ้าผู้เล่นทุบกล่องนั้นแล้ววาง chest ธรรมดาแทน (ต้องรีสตาร์ต) — ยอมรับได้เพราะ chest ปกติไม่มีทางติดบัญชีดำ
- **pulse สั้นมาก (sub-tick):** เพราะอ่านกำลังไฟใน tick ถัดไป pulse 1 tick จาก observer อาจพลาดบ้าง (trade-off ของ Folia-safety)
- **map `pistons`** เก็บ entry จนกว่า piston จะหาย (เคลียร์เมื่อ checkPiston พบว่าไม่ใช่ sticky piston แล้ว) — มีขอบเขตจำกัดอยู่แล้วเพราะเกิดเฉพาะ sticky piston ที่มีกระจกท่อติด
- **`InventoryMoveItemEvent` บน Folia ข้าม region:** ไม่ยิง (ดู §8d) → ท่อที่พาดข้าม region จริง ๆ ปลั๊กอินป้องกันพื้นที่จะมองไม่เห็น ยอมรับไว้เพราะการส่ง inventory ของอีก region ให้ listener อันตรายกว่า
- **storage unit ที่ปลายทางรับของคนละชนิด** ยังคัดได้เฉพาะตอน insert (ต้องถาม API ซึ่งผูกกับ Location) ต่างจากกฎ shulker ที่คัดได้ตั้งแต่ extract → ท่อที่ปลายทางมีแต่ storage unit ชนิดเดียวและต้นทางมีของชนิดอื่นปนจะเสีย pulse ไปเปล่า ๆ (ของไม่หาย)

---

## 9. Build, test & verify

```bash
.\gradlew.bat test           # รัน unit test (JUnit 5 + Mockito)
.\gradlew.bat shadowJar      # -> build/libs/PipePlugin-1.0.0.jar (FoliaLib relocate อยู่ภายใน)
```
- ตรวจว่า compile ผ่าน = ไม่ได้แตะ API นอก 1.20.1
- ตรวจคลาสใน jar: ต้องมีคลาสตามตารางข้อ 3 ครบ

### ทดสอบบนเซิร์ฟเวอร์จริงแบบอัตโนมัติ (ทำได้ ไม่ต้องรอคนเข้าเกม)

เคยเขียนไว้ว่า "ทดสอบในเกมต้องให้คนทำ" — **ไม่จริง** ขับผ่าน RCON ได้ทั้งหมด และทำไปแล้วบน
Spigot 1.20.1 (build เองด้วย BuildTools) / Paper 1.20.1 / Paper 26.2 / Folia 26.1.2

สูตรที่ใช้ได้จริง:
- โหลด jar จาก `fill.papermc.io/v3/projects/{paper,folia}/versions/<v>/builds`; JDK จาก
  `api.adoptium.net/v3/binary/latest/<ver>/ga/windows/x64/jdk/hotspot/normal/eclipse`
  (**26.x ต้องใช้ Java 25 ขึ้นไป**, 1.20.1 ใช้ 17–21)
- `server.properties`: `enable-rcon=true` + `rcon.port` + `rcon.password`, `level-type=minecraft:flat`,
  `online-mode=false` แล้วคุยด้วย RCON client เล็ก ๆ (โปรโตคอลง่ายมาก ~60 บรรทัด)
- สร้างท่อด้วย `/setblock` (`sticky_piston[facing=west]` ฯลฯ), **จ่ายไฟด้วยการวาง/ลบ `redstone_block`
  ข้าง redstone dust** — ต้องผ่าน dust เพราะ `BlockRedstoneEvent` ยิงจากตัว dust ไม่ใช่จากการวางบล็อก
- `/forceload add` ก่อนเสมอ ไม่งั้นคำสั่งวิ่งใส่ chunk ที่ไม่โหลด
- นับของ: `/data get block <x> <y> <z> Items[{Slot:Nb}].count` ทีละช่อง
  (**RCON ตัดข้อความยาว** ถ้าดึง `Items` ทั้งก้อน) — **1.20.5+ ใช้ `count`, ก่อนหน้านั้น `Count`**
- ระวัง `setblock <กล่อง> air` = ของในกล่องหล่นพื้น ต้อง `kill @e[type=item]` ตามทุกครั้ง
  ไม่งั้นการเช็ค "ไม่มีของตกพื้น" จะฟ้องผิด — และต้อง kill **หลัง** เคลียร์พื้นที่ ไม่ใช่ก่อน
  เพราะ `fill ... air` ที่กินพื้นใต้ redstone dust ทำให้ dust ป๊อปเป็นไอเทมตกพื้นด้วย
- **item frame เทสอัตโนมัติได้** (เคยเขียนว่าต้องให้คนทำ — ไม่จริง): frame ที่ `summon item_frame x y+1 z
  {Facing:1b,Item:{...}}` = เกาะบล็อกที่อยู่ **ใต้** มัน (`Facing` ชี้ออกจากผนัง) ส่วน NBT ของไอเทมข้างใน
  ใช้กฎ `count` / `Count` เดียวกับด้านบน
- **ทั้ง 3 เฟส (extract→insert→return) จบใน tick เดียวกัน** — ตั้งแต่มี `RegionExecutor` (§4.6) นี่เป็นจริง
  **โดยโครงสร้าง** ไม่ใช่เรื่องบังเอิญ: ทุกเฟสรันทันทีเมื่อ region เป็นของเธรดปัจจุบัน สอดคล้องกับผลวัดเดิม
  (hopper ที่จ่ายของเข้ากล่องต้นทางส่งได้ปกติเมื่อมีช่องว่างค้างไว้ = 8 ชิ้นใน 3 วิ แต่ยิง 120 pulse แล้ว
  **ไม่เคยแทรกระหว่างรอบได้เลยสักครั้ง**) → เคส "ช่องเดิมถูกยึดกลางรอบ" (TESTING 0e/6b) เกิดไม่ได้
  จากภายนอกบน platform นี้ ต้องพึ่ง unit test (`InventoryOpsTest`) เท่านั้น อย่าเสียเวลาไล่ซ้ำ

**Folia ต่างออกไป — จดไว้กันเสียเวลารอบหน้า:**
- ไม่ลงทะเบียนคำสั่ง `/data`, `/item`, `/clone`, `/loot` เลย และ `/execute if block|data` ก็ error
  → **อ่าน container จากนอกเซิร์ฟไม่ได้เลย** เหลือแต่ `setblock` / `fill` / `forceload` ที่ใช้ได้
- RCON คืน reply ว่างเปล่าเพราะคำสั่งถูก queue ไป region thread → ผลจริงอยู่ใน **console log**
- `setblock <กล่อง> air` ที่นี่ **ไม่ทำให้ของหล่นพื้น** จึงใช้วัดไม่ได้
- ✅ **วิธีที่ใช้อยู่ตอนนี้ (ดีกว่าการแกะไฟล์ region): ให้ปลั๊กอินเป็นคนวัดจากข้างใน**
  `Watch.jar` (ปลั๊กอินตัวแทน ~50 บรรทัด) log ทุก `InventoryMoveItemEvent` พร้อม **ยอดรวมของทั้งสองกล่อง
  ณ ตอนนั้น** ซึ่งอ่านได้อย่างถูกกฎบนเธรดที่อีเวนต์ถูกส่งมา แล้ว `FoliaTest.java` อ่านบรรทัดพวกนั้นกลับ
  จาก `console.log` มาตรวจเลข — ได้หลักฐานระดับเดียวกับ `/data` บน platform อื่น
  ใส่ของตั้งต้นด้วย `setblock 0 100 0 chest{Items:[{Slot:0b,id:"minecraft:dirt",count:64,Count:64b}]}`
  (ใส่ทั้ง `count` และ `Count` เผื่อเวอร์ชัน)
  **invariant ที่ตรวจ:** ณ ตอนอีเวนต์ยิง ของถูกดูดออกจากต้นทางแล้วแต่ยังไม่เข้าปลายทาง →
  `source + destination + in-flight = ยอดรวมเดิม` เสมอ (ระวัง off-by-one ตรงนี้ เคยเขียนผิดมาแล้ว)
- ทางเลือกสำรอง (ถ้าจำเป็นจริง ๆ): `/stop` แล้วแกะ `world/dimensions/minecraft/overworld/region/r.X.Z.mca`
  (เวอร์ชันใหม่ย้ายมาที่นี่ ไม่ใช่ `world/region/`) อ่าน `block_entities` ตรง ๆ

**BuildTools บน Windows:** พังด้วย `Filename too long` ถ้า path ลึก — build ใน path สั้น ๆ และตั้ง
`GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.longpaths GIT_CONFIG_VALUE_0=true` (ไม่ต้องแก้ git config ของเครื่อง)

### Unit tests (`src/test/java/...`)
testImplementation: spigot-api (เดิม compileOnly), `junit-jupiter`, `mockito-core`
รันบน JDK 24 → ตั้ง `net.bytebuddy.experimental=true` ใน `tasks.test` ให้ ByteBuddy รันบน JVM ที่ยังไม่ถูก whitelist

| ไฟล์ | ครอบคลุม |
|---|---|
| `DirectionsTest` | ตรรกะลำดับทิศ pure: leftOf, ordered, orderedWithBack, การันตี "ซ้ายก่อนขวา" |
| `MatchModeTest` | TYPE/SIMILAR, null, `fromConfig` (mock `ItemStack`) |
| `NetworkDiscoveryTest` | `isPipeGlass` (GLASS/TINTED + stained 16 สี = ท่อ, pane/ของอื่น = ไม่ใช่, ข้าม LEGACY), `facingOf` |
| `NetworkRegistryTest` | put/get, track ทุกส่วนของท่อ, invalidate ด้วยบล็อกใด ๆ, **บล็อกใช้ร่วมหลายท่อ invalidate ครบ**, remove ไม่ทิ้ง index ค้าง, isolation |
| `PipeConfigTest` | default, clamp ค่าติดลบ, interval→ms, `skip-plugin-managed-chests`, **`call-inventory-move-event` (default true + ปิดได้)**, **ENDER_CHEST ถูกปฏิเสธแม้ระบุใน list**, และ `allowed-containers` ครบทุกรูปแบบ: list, `all` ทุกตัวพิมพ์, **ค่าเดี่ยว `CHEST`**, หลายตัวคั่นจุลภาค, ชื่อไม่ใช่บล็อกถูกข้าม, **list ที่ผิดหมด = ปิดทุกกล่อง ไม่ใช่เปิดทุกกล่อง** (YamlConfiguration จริง + mock plugin/lang) |
| `PipeLangTest` | ข้อความของแอดมินชนะ, **key ที่เพิ่งเพิ่มตกไปใช้ค่าจาก jar ได้** (ทั้งแบบไม่มีหัวข้อ และมีหัวข้อแต่ไม่มี key ลูก), key ที่ไม่มีที่ไหนคืนชื่อ key, placeholder ซ้ำถูกแทนครบ, และ **key ทุกตัวที่โค้ดเรียกต้องมีจริงใน `lang.yml`** |
| `InventoryOpsTest` | ดูดของ (จำนวน/ช่องที่จำไว้/ข้ามชนิดอื่น/cap) + **คืนเข้าช่องเดิม**: คืนครบ, คืนบางส่วน, ช่องถูกยึด, ช่องเติมบางส่วน, กล่องหด, ของ stack ไม่ได้, **conservation 10 pulse ติดกันผังไม่เพี้ยน**, และ `countMatching` + **คู่เทสต์ inventory ที่โกหกสองแบบ** — (ก) `setItem` ไม่มีผล กับ (ข) **หักของจริงแต่วาดสต๊าคกลับ** — ที่อ่านจากภายนอกได้ผลเหมือนกันเป๊ะ ล็อกเหตุผลว่าทำไมต้องยัดของกลับทั้งหมด (invariant 1a); **+ ของ Material เดียวกันแต่ NBT ต่างกันต้องไม่ถูกดูดรวม/ไม่ถูกเติมทับ** (invariant 1e) |
| `ContainerAccessTest` | **กฎ shulker**: shulker ปฏิเสธ shulker ทุกสี, ของอื่นเข้า shulker ได้, shulker ลง chest ได้, และ `canHold` ตอบได้จาก Material ล้วน (invariant 1f); **ชั้นที่ 3 (บัญชีดำ)**: chest ปกติใช้ได้ → `distrust` แล้วทั้ง `inventoryOf`/`isUsable` ต้องปิดถาวร, `distrust` คืน true ครั้งเดียว (กันเตือนซ้ำ), key ไม่สนใจ yaw/pitch, กล่องข้าง ๆ / คนละโลก ไม่โดนหางเลข, `null` ปลอดภัย; **`trueCount`**: กล่องธรรมดานับจาก inventory, **storage unit นับจากเจ้าของ ไม่ใช่สต๊าคโชว์** (จำลองกล่องที่วาด 64 ค้างไว้), จำนวนเกิน `int` ยังวัดผลต่างได้เป๊ะ, กล่องที่เจ้าของไม่รับรู้ต้องตกกลับไปนับ inventory |
| `PipeNetworkTest` | `sameTopologyAs` (กระจกเพิ่ม/output ใหม่/ปลายทางเปลี่ยน/**ชนิดกล่องปลายทางเปลี่ยน**/สีเปลี่ยน/ต้นทางเปลี่ยน) + `adoptLockFrom` (รอบเก่ายังวิ่ง → รอบใหม่ถูกกัน, ปล่อยแล้วเริ่มได้) |
| `ExternalStorageGuardTest` | guard ที่ปิดต้องไม่บล็อกอะไรเลย + **ชั้นที่ 2**: คลาสของเซิร์ฟเวอร์ (มี/ไม่มีเลขเวอร์ชัน, double chest, paper) ผ่าน / คลาสของปลั๊กอินและชื่อที่ "เกือบเหมือน" ถูกปฏิเสธ; **`reportedCount`**: ไม่มีปลั๊กอิน storage ต้องคืน `null` (ห้ามคืน 0 — กล่องธรรมดาจะโดนขึ้นบัญชีดำหมด), ไม่ถูกปิดโดย config, `null` ปลอดภัย (ชั้นที่ 1 ต้องเทสในเกม) |
| `FrameIndexTest` | `passes`: ไม่มี gate = ผ่าน, gate match/ไม่ match, union หลาย frame, บล็อกอื่นไม่โดนกระทบ |
| `PipeRouterTest` | BFS routing (เส้นตรง, ไม่มี output, ใกล้ก่อนไกล, ทางแยกซ้ายก่อนขวา, หลาย output ต่อกระจก) **+ filter gate**: frame บน input กันดูด/ยอมดูด, บนกระจกกั้นแล้ว reroute ไปกิ่งขวา, บน output ปฏิเสธของไม่ตรง (ใช้ `GridWorld` + `FrameIndex.empty()`/`forTesting`) |
| `GridWorld` | โลกจำลอง (mock Block/World/Directional) เดิน getRelative/getLocation/getBlockAt ได้ — ใช้เทสต์ traversal โดยไม่ต้องรัน server |
| `BukkitStub` | เซิร์ฟเวอร์จำลองขั้นต่ำ (`Bukkit.setServer` + mock `ItemFactory`) — **จำเป็นตั้งแต่การจับคู่ภายในเปลี่ยนไปใช้ `isSimilar` เสมอ** เพราะ `ItemStack#isSimilar` เรียก `Bukkit.getItemFactory()` แล้ว NPE ถ้าไม่มีเซิร์ฟเวอร์; เรียกผ่าน `@BeforeAll` ใน `InventoryOpsTest`/`ContainerAccessTest` |

> **สิ่งที่ยัง unit test ไม่ได้:** ทั้ง pipeline ที่แตะ inventory จริง (extract/insert/return + การนับของ), การเกาะ item frame จริง (`getAttachedFace`), `discover()` บนบล็อกจริง, redstone/piston จริง, effects, หลาย platform
> — **ส่วนใหญ่ครอบด้วย integration test ผ่าน RCON บนเซิร์ฟเวอร์จริงแล้ว** (ดูสูตรด้านบน + TESTING.md §9)
> รวมถึง **item frame** ด้วย (`summon item_frame` — ดูสูตร) เหลือที่ต้องให้คนเทสมือจริง ๆ แค่
> **กล่องของปลั๊กอินอื่น (WildChests)** เพราะต้องมีปลั๊กอินนั้นลงอยู่บนเซิร์ฟ
> หมายเหตุ: `FrameIndex.empty()` / `FrameIndex.forTesting(map)` มีไว้ช่วยเทสต์ (ตัด/ยัด gate ตรง ๆ) — อย่าลบ

### สคริปต์ integration ที่มีอยู่ (ใน scratchpad, ไม่ได้อยู่ใน repo)

| ไฟล์ | ทำอะไร |
|---|---|
| `PipeTest.java` | 7 หัวข้อพื้นฐาน: ย้ายของ, shulker ปลายทาง, ender chest, ปลายทางเต็ม, ของ stack ไม่ได้, สีกระจกแยกเครือข่าย, effects |
| `RestTest.java` | 6d/5a: filter จริง + hopper ต้นทางที่ตัดทาง vanilla ทิ้ง + control experiment ของ 0e/6b |
| `RulesTest.java` | กฎใหม่: shulker ไม่ซ้อน shulker + **ไม่ทำให้ท่อตัน**, shulker ลง chest ได้, และโหมด `cancelled` = ปลั๊กอินป้องกันยกเลิกแล้วของต้องไม่ขยับและไม่หาย |
| `FoliaTest.java` | Folia โดยเฉพาะ — วัดจาก log ของ `Watch.jar` (ดูหัวข้อ Folia ด้านบน) |
| `AllowTest.java` + `allow2.sh`/`allow3.sh` | `allowed-containers` ทั้ง 5 รูปแบบ (รีสตาร์ตเซิร์ฟทุกแบบ) |
| `Watch.jar` (`watcher/`) | ปลั๊กอินตัวแทน "ปลั๊กอินป้องกันพื้นที่": log ทุก `InventoryMoveItemEvent` + ยอดรวมสองกล่อง, cancel เมื่อมีไฟล์ `plugins/Watch/cancel` |
| `hardened.sh` | รันทุกชุดข้างบนบนเซิร์ฟหนึ่งตัว แล้วรีสตาร์ตซ้ำในโหมด cancel + เช็ค console สะอาด |

---

## 10. Out of scope (ตั้งใจไม่ทำ)

GUI, คำสั่ง admin/visualize, permission ละเอียด, ปรับ rate/ฟิลเตอร์แยกต่อท่อ (rate เป็นค่ารวมใน config)
(ออกแบบเผื่อขยายได้แต่ยังไม่ทำ — ถ้าจะเพิ่ม ให้คงหลักการข้อ 4 และอัปเดตไฟล์นี้)

**อยู่ในขอบเขตแล้ว:** หลาย **output** ต่อท่อ; หลาย **input** (sticky piston หลายตัว) บนกระจกสีเดียวกัน =
คนละ network ที่ใช้ท่อร่วม ต่างคนต่างทำงาน (ตั้งใจ ไม่ reject) — reverse index เป็น multimap แล้ว
การทุบบล็อกที่ใช้ร่วมจึง invalidate **ทุก** network ที่ใช้บล็อกนั้นถูกต้อง (ไม่มี network ค้าง cache เก่า)
