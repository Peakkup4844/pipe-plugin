# PipePlugin

A *Create*-style item pipe system for Minecraft servers.
ระบบท่อขนไอเทมสไตล์ม็อด *Create* สำหรับเซิร์ฟเวอร์ Minecraft

Move items between storage blocks by building a pipe out of stained glass and pulsing redstone — no GUI, no commands, just build and power it.
ย้ายไอเทมระหว่างกล่องเก็บของด้วยการต่อท่อจากกระจกสีแล้วจ่ายไฟ redstone — ไม่มี GUI ไม่มีคำสั่ง แค่สร้างแล้วจ่ายไฟ

---

## Compatibility / ความเข้ากันได้

| | |
|---|---|
| Minecraft | **1.20.1 → latest (26.1)** |
| Platforms | **Bukkit / Spigot, Paper, Folia** |
| Java (runtime) | 17+ (the jar is compiled to Java 17 bytecode) |

Built against the pure Bukkit API (no NMS), so a single jar runs across the whole version range. Folia support is provided through [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) (shaded & relocated).

คอมไพล์ด้วย Bukkit API ล้วน (ไม่มี NMS) → jar เดียวรันได้ทุกเวอร์ชันในช่วงที่ระบุ และรองรับ Folia ผ่าน FoliaLib ที่ shade + relocate มาให้แล้ว

---

## How it works / หลักการทำงาน

A pipe has three connected parts:
ท่อหนึ่งเส้นประกอบด้วย 3 ส่วนที่เชื่อมต่อกัน:

```
[ Source chest ] ← (Sticky Piston)   ==glass tube==   (Piston) → [ Dest chest ]
     ต้นทาง          input หันเข้ากล่อง   ท่อกระจกสีเดียว    output หันเข้ากล่อง   ปลายทาง
```

1. **Input** — a **sticky piston** whose head faces a storage block (the source).
   sticky piston หันหัวเข้ากล่องต้นทาง
2. **Process** — a tube of **same-coloured glass** running from the sticky piston to the output.
   ท่อกระจก **สีเดียวกันทั้งเส้น** ลากจาก sticky piston ไปยัง output
3. **Output** — a normal **piston** whose head faces another storage block (the destination).
   piston ธรรมดาหันหัวเข้ากล่องปลายทาง

All three parts must touch with **no gap**. The glass colour defines the network — **each colour is a separate, independent pipe**, so different-coloured pipes can run side by side without connecting.

ทั้ง 3 ส่วนต้องติดกันไม่ขาด สีของกระจกคือตัวกำหนดเครือข่าย — **กระจกแต่ละสี = ท่อแยกกันคนละเส้น** ท่อต่างสีวางติดกันได้โดยไม่เชื่อมกัน

### Activating / การเปิดใช้งาน

Send a redstone **pulse** to the sticky piston. **Each pulse = 1 cycle = moves up to 32 items** (configurable) from source to destination.

จ่าย redstone แบบ **pulse** เข้า sticky piston — **1 pulse = 1 รอบ = ย้ายของสูงสุด 32 ชิ้น** (ปรับได้) จากต้นทางไปปลายทาง

> The pistons act purely as markers/triggers — they never physically push the chests.
> piston ทำหน้าที่เป็นแค่ตัว trigger ไม่ได้ดันกล่องจริง

---

## Supported storage / กล่องที่รองรับ

Any container block: chest, trapped chest, double chest, barrel, shulker box, hopper, dropper, dispenser, etc. (anything that is a Bukkit `Container`).
กล่องเก็บของทุกชนิดที่เป็น `Container`: หีบ, หีบกับดัก, หีบคู่, ถัง, shulker, hopper, dropper, dispenser ฯลฯ

Supported glass: clear `GLASS`, `TINTED_GLASS`, and all 16 `*_STAINED_GLASS` blocks (full blocks, not panes).
กระจกที่ใช้ได้: กระจกใส, tinted glass และกระจกสีทั้ง 16 สี (บล็อกเต็ม ไม่ใช่บานกระจก/pane)

---

## Installation / การติดตั้ง

1. Drop `PipePlugin-1.0.0.jar` into your server's `plugins/` folder.
   วางไฟล์ jar ลงในโฟลเดอร์ `plugins/`
2. Start the server. A `plugins/PipePlugin/config.yml` is generated.
   เปิดเซิร์ฟเวอร์ จะได้ไฟล์ config อัตโนมัติ

---

## Configuration / การตั้งค่า

`config.yml`:

```yaml
# Items moved PER ITEM TYPE per pulse (counted as individual items, not stacks)
# จำนวน item ที่ย้าย "ต่อชนิด" ต่อ 1 pulse (นับเป็นชิ้น ไม่ใช่ stack)
items-per-cycle: 32

# Max tube length the search will follow (caps the pathfinding cost)
# ความยาวท่อสูงสุดที่ระบบจะไล่หา (จำกัดภาระการค้นหา)
max-pipe-length: 64

# Item-frame filter matching mode / โหมดเทียบไอเทมของ filter
#   SIMILAR = exact NBT (type + custom name + enchants + meta)  [default]
#   TYPE    = material only (all diamonds count as the same)
filter-match: SIMILAR

# Allowed source/destination storage:
#   all  = any Container
#   list = only the listed materials
# storage ที่อนุญาต: all = ทุก Container, หรือระบุเป็น list
allowed-containers: all
```

---

## Quick start example / ตัวอย่างเริ่มต้นเร็ว

1. Place a chest, fill it with items. / วางกล่องแล้วใส่ของ
2. Place a **sticky piston** against the chest, head pointing into it. / วาง sticky piston หันหัวเข้ากล่อง
3. From the sticky piston, run a line of **one colour** of glass to where you want. / ต่อกระจกสีเดียวจาก sticky piston ไปยังจุดหมาย
4. At the end, place a **piston** against a second chest, head pointing into it, touching the glass. / ปลายทางวาง piston หันเข้ากล่องอีกใบ ให้ติดกับกระจก
5. Pulse redstone (button/lever) into the sticky piston → 32 items move per pulse. / จ่ายไฟ pulse เข้า sticky piston → ย้าย 32 ชิ้นต่อครั้ง

---

## Building from source / การ build เอง

Requires a JDK 17+ on `PATH` (the included Gradle wrapper handles the rest).
ต้องมี JDK 17+ ใน `PATH` (Gradle wrapper จัดการที่เหลือให้)

```bash
./gradlew shadowJar        # Linux/macOS
.\gradlew.bat shadowJar    # Windows
```

Output: `build/libs/PipePlugin-1.0.0.jar`

---

## Item-frame filters / ตัวกรองด้วยกรอบรูป

Place an **item frame** on the boundary between any two pipe parts and put an item in it. That frame becomes a **gate**: an item may cross that boundary only if it matches the framed item. No frame = everything passes.

แปะ **item frame** ลงบนรอยต่อระหว่างชิ้นส่วนของท่อตรงไหนก็ได้ แล้วใส่ไอเทมลงในเฟรม เฟรมนั้นจะเป็น **ประตูคัดของ**: ของจะข้ามรอยต่อนั้นได้ก็ต่อเมื่อ match กับไอเทมในเฟรม ถ้าไม่มีเฟรม = ผ่านได้หมด

| Frame location / ตำแหน่งเฟรม | Effect / ผล |
|---|---|
| Input piston ↔ tube | Only matching items are **pulled** from the source / ดูดเฉพาะของที่ตรง |
| Tube ↔ tube | Items that don't match **can't pass beyond** that point / ไม่ผ่านเลยจุดนั้น |
| Tube ↔ output piston | That output **won't accept** non-matching items / ปลายทางนั้นไม่รับ |

- Multiple frames on the same boundary → match **any** of them passes (union). / หลายเฟรมบนรอยต่อเดียว = ตรงอันใดอันหนึ่งก็ผ่าน
- Matching is **NBT-exact by default** (`SIMILAR`): a renamed item only matches the exact renamed item. Switch to `TYPE` in config for material-only matching. / ค่าเริ่มต้นเทียบ NBT เป๊ะ — ของเปลี่ยนชื่อจะรับเฉพาะอันที่ชื่อตรง
- Frames are read live every pulse, so changing/removing a frame takes effect immediately. / อ่านเฟรมสดทุก pulse เปลี่ยน/ถอดเฟรมมีผลทันที

## Multiple outputs & routing / หลายปลายทางและการจัดเส้นทาง

One pipe (one input) can feed **many output pistons** — just attach more pistons (each facing a container) anywhere along the tube.

ท่อเส้นเดียว (1 input) ป้อนได้ **หลาย output** — แค่เอา piston (หันเข้ากล่อง) ไปแปะตามแนวท่อเพิ่ม

Per pulse, each item type is routed independently:
ต่อ pulse ของแต่ละชนิดถูกจัดเส้นทางแยกกัน:

1. **Priority = nearest first, then left before right.** At a junction the left branch is preferred; if the left branch is blocked by a filter for that item, it goes right instead.
   ลำดับ = ใกล้ก่อน แล้วซ้ายก่อนขวา; ที่ทางแยกเลือกซ้ายก่อน ถ้าซ้ายโดน filter กั้นค่อยไปขวา
2. **Priority fill:** the highest-priority output is filled until full, then overflow goes to the next, and so on.
   เติมตามลำดับ: ปลายทางอันดับแรกเต็มก่อนค่อยล้นไปอันถัดไป
3. Anything that fits nowhere is returned to the source (never lost).
   ของที่ไม่มีที่ลงเลยถูกคืนต้นทาง (ไม่หาย)

> "Front" of the pipe = the direction leading **away from the source**; left/right are from that facing.
> "หน้า" ของท่อ = ทิศที่ออกจากต้นทาง ส่วนซ้าย/ขวาอ้างอิงจากการหันหน้านั้น

## Behaviour & limits / พฤติกรรมและข้อจำกัด

- Source empty → nothing moves. Destination full → only what fits is moved; the rest stays in the source (items are never lost).
  ต้นทางว่าง = ไม่ย้าย / ปลายทางเต็ม = ย้ายเท่าที่ลงได้ ที่เหลือคืนต้นทาง (ของไม่หาย)
- Pipes are auto-discovered on activation and cached; breaking/changing any block in the pipe re-validates it on the next pulse.
  ระบบค้นหาท่ออัตโนมัติตอนจ่ายไฟแล้ว cache ไว้ การทุบ/แก้บล็อกในท่อจะตรวจใหม่ในรอบถัดไป
- Triggering relies on `BlockRedstoneEvent` rising-edge detection at the sticky piston. Standard redstone inputs (lever, button, repeater adjacent to the piston) work as expected.
  การ trigger ใช้การจับขอบขาขึ้นของ redstone ที่ sticky piston — การจ่ายไฟทั่วไป (คันโยก/ปุ่ม/repeater ติด piston) ใช้ได้ปกติ
- On Folia, item insertion happens on each output region's own thread, so outputs in different regions are handled safely. The pipe **topology/filter scan** runs on the input's region, so for guaranteed correctness keep a single pipe network within one region (the normal case for a connected build).
  บน Folia การใส่ของทำบนเธรดของแต่ละ region ปลายทาง จึงปลอดภัย ส่วนการสแกนโทโพโลยี/ฟิลเตอร์ทำบน region ของ input ดังนั้นเพื่อความถูกต้องเต็มที่ ควรให้ท่อหนึ่งเครือข่ายอยู่ใน region เดียว (ซึ่งเป็นกรณีปกติของสิ่งก่อสร้างที่ต่อกัน)

---

## License

You may use and modify this freely for your server.
