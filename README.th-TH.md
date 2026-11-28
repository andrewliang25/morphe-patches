# 🧩 Andrew's Patches

[English](README.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja-JP.md) | ไทย | [Bahasa Indonesia](README.id-ID.md)

แพตช์ Morphe สำหรับ LINE และ Facebook

## ❓ เกี่ยวกับโปรเจกต์

นี่คือแพตช์ [Morphe](https://github.com/MorpheApp) สำหรับ [LINE](https://line.me)
(`jp.naver.line.android`) และ Facebook (`com.facebook.katana`) ใช้ Morphe CLI หรือ
Morphe Manager เพื่อใส่แพตช์และสร้าง APK ที่แก้ไขแล้ว

> 💡 **ไม่อยากสร้างเอง?** ดาวน์โหลดแอปที่ใส่แพตช์แล้วได้จาก
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases)

> โปรเจกต์นี้ไม่มีความเกี่ยวข้องกับโปรเจกต์โอเพนซอร์ส Morphe, LINE, LY Corporation,
> Facebook หรือ Meta ทั้งหมดนี้ไม่ได้รับรองโปรเจกต์นี้ และไม่ได้มีส่วนในการเขียน

## 🩹 รายการแพตช์

คำอธิบายแพตช์สร้างและแปลโดยอัตโนมัติ ชื่อแพตช์ยังเป็นภาษาอังกฤษ

<!-- PATCHES_START EXPANDED -->
> **[v4.2.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v4.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;รวม 62 แพตช์
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;20 แพตช์</summary>
<br>

**🎯 เวอร์ชันที่รองรับ:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;แพตช์ | 📜&nbsp;คำอธิบาย | ⚙️&nbsp;ตัวเลือก |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | หยุด Facebook ไม่ให้ตรวจจับภาพหน้าจอของโฆษณา และไม่ให้รายงานว่าคุณติดตั้งแอปอะไรบ้างเพื่อใช้อ้างอิงโฆษณา |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | หยุด Facebook ไม่ให้ดาวน์โหลดโฆษณาและโมเดลโฆษณาในเบื้องหลัง ช่วยประหยัดข้อมูล แบตเตอรี่ และพื้นที่จัดเก็บ |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | หยุด Facebook ไม่ให้แสดงโฆษณาในแอปอื่น แอปเหล่านั้นจะแสดงโฆษณาของตนเองหรือไม่แสดงเลย และโฆษณาแบบให้รางวัลอาจล้มเหลว |  |
| [[Feed] Block feed auto refresh](#feed-block-feed-auto-refresh) | คงตำแหน่งของคุณในฟีดข่าวเมื่อคุณกลับมาที่ Facebook ดึงลงเพื่อรีเฟรชฟีด |  |
| [[Feed] Hide page suggestions and promo cards](#feed-hide-page-suggestions-and-promo-cards) | ลบรายการที่ Facebook เพิ่มในฟีด เช่น "เพจที่คุณอาจชอบ" การ์ดโปรโมชัน และแบบสำรวจ โพสต์ "แนะนำสำหรับคุณ" ยังอยู่ |  |
| [[Feed] Hide post prompts](#feed-hide-post-prompts) | ลบข้อความถามที่ Facebook เพิ่มในโพสต์ เช่น "คุณสนใจโพสต์นี้หรือไม่" |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | ลบโพสต์ที่ได้รับการสนับสนุนออกจากฟีดข่าว โดยไม่ทิ้งช่องว่าง |  |
| [[Fix] Restore screens on re-signed builds](#fix-restore-screens-on-re-signed-builds) | ทำให้โปรไฟล์และบางหน้าในการตั้งค่าเปิดได้อีกครั้งบนบิลด์ที่เซ็นชื่อใหม่ การติดตั้งแบบ Root Mount ไม่ต้องใช้แพตช์นี้ |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | ทำให้โหมดมืดของ Facebook เป็นสีดำแทนสีเทาเข้ม โปรดเปิดโหมดมืดใน Facebook ก่อน ปิดได้ใน Andrew's Patch Setting |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | เพิ่ม "Andrew's Patch Setting" ที่ด้านบนของเมนู Facebook ที่นี่คุณเปิดหรือปิดบางแพตช์ได้โดยไม่ต้องแพตช์ใหม่ และดูเครดิตกับสัญญาอนุญาต |  |
| [[General] Hide affiliate product links](#general-hide-affiliate-product-links) | ลบการ์ดสินค้าของลิงก์ร้านค้าพันธมิตรออกจาก Reels โพสต์ในฟีด และความคิดเห็น ป้าย "มีสิทธิ์รับค่าคอมมิชชัน" ยังอยู่ |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | เปิดลิงก์เว็บในเบราว์เซอร์เริ่มต้นของคุณ แทนเบราว์เซอร์ในแอปของ Facebook หน้า Facebook ยังเปิดในแอป |  |
| [[Reels] Download any reel](#reels-download-any-reel) | เพิ่มปุ่มดาวน์โหลดข้างทุก Reel วิดีโอบันทึกด้วยคุณภาพสูงสุดที่ตัวเล่นสตรีม | • บันทึกเป็น H.264 |
| [[Reels] Hide interest prompts](#reels-hide-interest-prompts) | ลบข้อความถาม "คุณสนใจ Reel นี้หรือไม่" ออกจาก Reels |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | ลบโฆษณาออกจาก Reels และ Watch รวมถึงแบนเนอร์สินค้าบน Reel และโฆษณาในวิดีโอ |  |
| [[Reels] Picture-in-picture](#reels-picture-in-picture) | เล่น Reel ต่อในหน้าต่างเล็กเมื่อคุณออกจาก Facebook จากแท็บ Reels เฉพาะ Reels แนวตั้งที่มีหน้าต่าง ต้องใช้ Android 12 ขึ้นไป |  |
| [[Stories] Disable auto advance](#stories-disable-auto-advance) | คงสตอรี่แต่ละรายการบนหน้าจอจนกว่าคุณจะแตะหรือปัดไปรายการถัดไป |  |
| [[Stories] Download any story](#stories-download-any-story) | เพิ่ม บันทึก ในเมนูของสตอรี่ทุกรายการ รวมถึงสตอรี่ที่มีเพลง วิดีโอบันทึกด้วยคุณภาพสูงสุดที่ตัวเล่นสตรีม | • บันทึกเป็น H.264 |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | ลบการ์ดโฆษณาออกจากตัวดูสตอรี่ การปัดดูสตอรี่จึงเห็นเฉพาะสตอรี่ที่ผู้คนโพสต์ |  |
| [[Stories] View stories anonymously](#stories-view-stories-anonymously) | หยุด Facebook ไม่ให้บอกเซิร์ฟเวอร์ว่าคุณดูสตอรี่ใด คุณจึงไม่อยู่ในรายชื่อผู้ดู สตอรี่ที่คุณดูแล้วยังแสดงว่าดูแล้วบนอุปกรณ์นี้ |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;42 แพตช์</summary>
<br>

**🎯 เวอร์ชันที่รองรับ:**

| 26.16.0 | 26.17.1 | 26.18.2 | 26.19.0 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;แพตช์ | 📜&nbsp;คำอธิบาย | ⚙️&nbsp;ตัวเลือก |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | ซ่อนโฆษณาแบบแสดงผลของ LINE ได้แก่ แบนเนอร์ LINE Ads แบนเนอร์ Smart Channel ในรายการแชท และโฆษณา Google AdManager |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | หยุด LINE ไม่ให้โหลดแบนเนอร์โฆษณา Smart Channel |  |
| [[Call] Auto-record calls](#call-auto-record-calls) | เริ่มบันทึกสายแบบ 1 ต่อ 1 ทันทีที่สายเชื่อมต่อ โปรดเปิดการบันทึกสายใน การตั้งค่า > LINE Labs ก่อน การบันทึกโดยไม่ได้รับความยินยอมผิดกฎหมายในบางพื้นที่ |  |
| [[Call] Record calls without notifying](#call-record-calls-without-notifying) | อีกฝ่ายในสายแบบ 1 ต่อ 1 จะไม่เห็นแบนเนอร์การบันทึกและไม่ได้ยินเสียงประกาศ การบันทึกโดยไม่ได้รับความยินยอมผิดกฎหมายในบางพื้นที่ |  |
| [[Call] Remove recording time limit](#call-remove-recording-time-limit) | บันทึกสายจนกว่าสายจะจบ LINE หยุดการบันทึกเสียงเมื่อครบ 60 นาที และหยุดการบันทึกวิดีโอเมื่อครบ 20 หรือ 50 นาที |  |
| [[Call] Save recordings to device](#call-save-recordings-to-device) | บันทึกไฟล์เสียงสายแต่ละสายลงในโฟลเดอร์ Movies/LINE บนโทรศัพท์ แทนการแชร์ไปยัง Keep Memo ซึ่งไม่รับไฟล์ที่เกิน 1 GB |  |
| [[Chat] Hide AI Friends button](#chat-hide-ai-friends-button) | ลบปุ่ม AI Friends ออกจากส่วนหัวของแท็บแชท แล้วแสดงปุ่มอัลบั้มแทน |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | ลบแถว "กิจกรรม" ออกจากเมนูในห้องแชท |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | ลบไทล์ LINE GIFT ออกจากเมนูแนบไฟล์ + ในห้องแชท |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | ลบไทล์โอนเงิน (LINE Pay) ออกจากเมนูแนบไฟล์ + ในห้องแชท |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | ลบเครื่องมือที่เซิร์ฟเวอร์ให้มา (โพล กำหนดการ และอื่น ๆ) ออกจากเมนู + ในแชท ไทล์ที่มีในตัวยังอยู่ |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | ลบปุ่มปฏิทิน LINE ทั้งหมด ได้แก่ ในแท็บแชท แถบเครื่องมือและเมนูของแชท และแท็บโฮม |  |
| [[Chat] Hide community button](#chat-hide-community-button) | ลบปุ่มคอมมูนิตี้ (OpenChat) ออกจากด้านบนของส่วนหัวแท็บแชท |  |
| [[Chat] Hide tips under messages](#chat-hide-tips-under-messages) | ซ่อนทิปส์ "CHECK" ใต้ข้อความบางรายการ เช่น วิดีโอที่ได้รับ |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | เมื่อคุณเปิดแชท LINE จะไม่ทำเครื่องหมายว่าอ่านแล้วและไม่ส่งการแจ้งว่าอ่านแล้ว "ทำเครื่องหมายว่าอ่านแล้ว" ยังใช้ได้ |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | เก็บข้อความที่คนอื่นยกเลิกการส่งในแชท 1:1 และแชทกลุ่ม พร้อมป้ายสีแดง "ยกเลิกการส่ง" ข้างเวลา ไม่รวม OpenChat |  |
| [[Chat] Show who read messages](#chat-show-who-read-messages) | เพิ่ม "อ่านโดย" ในเมนูกดค้างของข้อความในกลุ่ม แสดงรายชื่อสมาชิกที่อ่านข้อความนั้น |  |
| [[Fix] Restore Google login via MicroG-RE](#fix-restore-google-login-via-microg-re) | ทำให้ "ดำเนินการต่อด้วย Google" ใช้ได้บนบิลด์ที่เซ็นชื่อใหม่ ผ่าน MicroG-RE ไม่จำเป็นสำหรับ Root Mount |  |
| [[Fix] Restore biometric login](#fix-restore-biometric-login) | เชื่อมการเข้าสู่ระบบด้วยไบโอเมตริกซ์ได้อีกครั้งบนบิลด์ที่เซ็นชื่อใหม่ การติดตั้งแบบ Root Mount ไม่ต้องใช้แพตช์นี้ |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | ทำให้การสำรองและกู้คืนแชทบน Google Drive ใช้ได้บนบิลด์ที่เซ็นชื่อใหม่ ผ่าน MicroG-RE ไม่จำเป็นสำหรับ Root Mount |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | แสดงแผนที่อีกครั้งบนบิลด์ที่เซ็นชื่อใหม่ ด้วยแผนที่ OpenFreeMap ต้องใช้ MicroG-RE 7.0.0 ขึ้นไป ไม่จำเป็นสำหรับ Root Mount |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | เมื่อปิด LINE สนิท การแจ้งเตือนแบบพุชจะกลับมาทำงานบนบิลด์ที่เซ็นชื่อใหม่ และ LINE จะได้รับ Firebase Remote Config อีกครั้ง การติดตั้งแบบ Root Mount ไม่ต้องใช้แพตช์นี้ |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | เพิ่ม "Andrew's Patch Setting" ในการตั้งค่า LINE ใต้ "โปรไฟล์" ที่นี่คุณเปิดหรือปิดบางแพตช์ได้โดยไม่ต้องแพตช์ใหม่ และดูเครดิตกับสัญญาอนุญาต |  |
| [[General] Block analytics](#general-block-analytics) | หยุด LINE ไม่ให้ส่งเหตุการณ์การใช้งาน เช่น หน้าจอที่คุณเปิดและปุ่มที่คุณแตะ ไปยังบริการวิเคราะห์ รายงานข้อขัดข้องไม่เปลี่ยนแปลง |  |
| [[General] Custom font](#general-custom-font) | เพิ่มปุ่มในหน้าฟอนต์ของ LINE เพื่อใช้ไฟล์ฟอนต์จากอุปกรณ์ของคุณ จากนั้นหน้านี้จะไม่โหลดรายการฟอนต์ของ LINE จากเซิร์ฟเวอร์อีก |  |
| [[General] Disable VOOM](#general-disable-voom) | ลิงก์ลึก การแชร์ และการแจ้งเตือนของ VOOM จะไม่ทำงาน หากเปิดฟีด VOOM แบบแยกต่างหาก ฟีดจะปิด การส่งข้อความและแท็บอื่นไม่เปลี่ยนแปลง |  |
| [[General] Enable themes on sub devices](#general-enable-themes-on-sub-devices) | ให้อุปกรณ์รอง (แท็บเล็ตหรือโทรศัพท์เครื่องที่สองที่ล็อกอินบัญชีเดียวกัน) ใช้ธีมและธีมสีดำในโหมดมืดได้ ซ่อนปุ่มร้านค้าธีมที่นั่น เพราะ LINE ไม่เปิดร้านค้าบนอุปกรณ์รอง |  |
| [[General] Hide Agent i buttons](#general-hide-agent-i-buttons) | ลบ Agent i ออกจากส่วนหัวโฮม แถบค้นหา ส่วนหัวแท็บกระเป๋าเงิน และการตั้งค่า และซ่อน Agent i ในแชทถาวร พร้อมสวิตช์ "แสดง Agent i ในแชท" ในเมนู + |  |
| [[General] Hide LINE AI image editing](#general-hide-line-ai-image-editing) | ลบปุ่มแก้ไขรูปด้วย AI ของ LINE ได้แก่ "AI Edit" ในเมนูกดค้างของข้อความ ปุ่มข้างข้อความรูปภาพ และปุ่มในตัวดูรูปของแชท อัลบั้ม โน้ต และรูปโปรไฟล์ ตัวแก้ไขรูปปกติยังอยู่ |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | ซ่อนจุดสีเขียวและป้าย N ที่ระบุรายการใหม่ บนปุ่มส่วนหัว แท็บ เมนู รายการ และแถวการตั้งค่า จำนวนข้อความที่ยังไม่ได้อ่านไม่เปลี่ยนแปลง |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | เปิดลิงก์เว็บในเบราว์เซอร์เริ่มต้นของคุณ แทนเบราว์เซอร์ในแอปของ LINE มินิแอป LIFF และลิงก์ LINE ยังอยู่ใน LINE |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | เปิด LINE Pay ในแอป LINE Pay แบบแยก เพื่อไม่ให้การตรวจสอบความสมบูรณ์ที่ล้มเหลวบนบิลด์ที่เซ็นชื่อใหม่ทำงาน |  |
| [[General] Trust user-installed CAs](#general-trust-user-installed-cas) | ให้ LINE เชื่อถือผู้ออกใบรับรองที่คุณติดตั้ง เพื่อให้พร็อกซี HTTPS ของคุณอ่านทราฟฟิกเว็บของ LIFF และมินิแอปได้ ธนาคารและ LINE Pay ยังคงตรึงใบรับรอง สำหรับการดีบัก ปิดไว้เป็นค่าเริ่มต้น |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | ลบฟีดเนื้อหาใต้รายชื่อเพื่อนในแท็บโฮม ได้แก่ LINE NEWS โพสต์ของบัญชีทางการ และอันดับ |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | ซ่อนโมดูลเนื้อหาแนะนำ หัวข้อฮอต และโฆษณาในแท็บโฮม |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | ซ่อนข้อเสนอ ป้าย และหน้าของ LINE Premium ทั้งหมด ไม่ปลดล็อกอะไร เพราะเซิร์ฟเวอร์เป็นผู้บังคับใช้ Premium |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | ลบข้อเสนอ Premium สำหรับการยกเลิกการส่งที่ "Disable LINE Premium" ทิ้งไว้ การยกเลิกการส่งแบบปกติยังใช้ได้ |  |
| [[Premium] Unlock app icons](#premium-unlock-app-icons) | ให้คุณใช้ไอคอนแอปได้ทุกแบบ รวมถึงไอคอน Premium และไอคอนคอลแลบเก่า หน้าไอคอนแอปยังอยู่ในการตั้งค่าแม้เปิด "Disable LINE Premium" |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | ลบแท็บ LINE TODAY (ข่าว) ออกจากแถบด้านล่าง |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | ลบแท็บช้อปปิ้งออกจากแถบด้านล่าง รวมถึง Discover (逛逛) ในไต้หวัน |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | ลบแท็บ VOOM (เดิมคือไทม์ไลน์) ออกจากแถบนำทางหลักด้านล่าง |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | ลบแท็บกระเป๋าเงิน (LINE Pay) ออกจากแถบด้านล่าง |  |

</details>

<!-- PATCHES_END -->

#### วิธีใช้แพตช์เหล่านี้

คลิกที่นี่เพื่อเพิ่มแพตช์เหล่านี้ลงใน Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

หรือเพิ่ม URL ของรีโพสิทอรีนี้เป็นแหล่งแพตช์ใน Morphe: https://github.com/andrewliang25/morphe-patches

#### เปลี่ยนแพตช์ในแอป

แพตช์ "[General] Andrew's Patch Setting" ช่วยให้คุณเปิดหรือปิดแพตช์ส่วนใหญ่ได้โดยไม่ต้องใส่แพตช์ใหม่
ใน LINE ให้เปิด **ตั้งค่า** แล้วเปิด **Andrew's Patch Setting** ใต้ **โปรไฟล์** ใน Facebook ให้เปิด
**เมนู** (☰) แล้วเปิด **Andrew's Patch Setting** ที่ด้านบนสุด

การเปลี่ยนแปลงบางอย่างจะแสดงหลังจากรีสตาร์ทแอปเท่านั้น ในกรณีนี้หน้าจอจะแสดงปุ่มสำหรับรีสตาร์ท
แพตช์ที่คุณไม่ได้ใส่ในบิลด์จะไม่มีสวิตช์

## 🛠️ การสร้าง

ในการสร้าง Andrew's Patches ให้ทำตามขั้นตอนใน
[เอกสารของ Morphe](https://github.com/MorpheApp/morphe-documentation)

## ⚠️ ข้อจำกัดที่ทราบ

### LINE: ร้านค้าบางแห่งไม่รับ LINE Pay

**ปัญหา:** ที่ร้านค้าบางแห่ง การจ่ายเงินด้วย LINE Pay จะเปิดแอป **LINE Pay** แยก และแอปนั้นแสดงข้อผิดพลาด
([#161](https://github.com/andrewliang25/morphe-patches/issues/161), [#220](https://github.com/andrewliang25/morphe-patches/issues/220))

**สาเหตุ:** *[General] Redirect LINE Pay* ส่งการจ่ายเงินไปที่แอปแยก เพราะ LINE Pay ใน LINE ที่ถูกแก้ไข
ไม่ผ่านการตรวจสอบความสมบูรณ์ ร้านค้าบางแห่งรับการจ่ายเงินจากใน LINE เท่านั้น แพตช์ไม่แตะโค้ดการจ่ายเงิน
เพราะอาจทำให้บัญชีถูกแบน

**วิธีแก้ชั่วคราว:** จ่ายเงินด้วยวิธีอื่นที่ร้านค้าเหล่านี้

### LINE: Google passkey ใช้ไม่ได้ (บิลด์ที่เซ็นใหม่)

**ปัญหา:** ในบิลด์ที่ใส่แพตช์ (Standard) คุณไม่สามารถเข้าสู่ระบบด้วย passkey หรือสร้าง passkey ได้
([#256](https://github.com/andrewliang25/morphe-patches/issues/256))

**สาเหตุ:** บริการ Google Play อนุญาต passkey เฉพาะลายเซ็นดั้งเดิมของ LINE เท่านั้น แพตช์เปลี่ยนส่วนนี้ไม่ได้
และ MicroG-RE ก็ช่วยไม่ได้

**วิธีแก้ชั่วคราว:** ติดตั้งแบบ **Root Mount** หรือเข้าสู่ระบบด้วยหมายเลขโทรศัพท์และรหัสผ่าน หรือด้วย
QR code

### Facebook: ติดตั้งแอปอื่นของ Meta ไม่ได้ (บิลด์ที่เซ็นใหม่)

**ปัญหา:** เมื่อติดตั้ง **Facebook** ที่ใส่แพตช์แล้ว **Facebook Lite** และ **Messenger** จะติดตั้งไม่สำเร็จ
พร้อมข้อผิดพลาด `INSTALL_FAILED_DUPLICATE_PERMISSION`

**สาเหตุ:** แอปของ Meta ใช้ชื่อสิทธิ์ร่วมกัน และ Android อนุญาตเฉพาะเมื่อแอปมีลายเซ็นเดียวกัน บิลด์ที่ใส่แพตช์
ถูกเซ็นใหม่ ปัญหาเดียวกันเกิดใน [patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict)

**วิธีแก้ชั่วคราว:** ติดตั้งแบบ **Root Mount** หรือเก็บไว้เพียงแอปเดียวจากสองแอปนี้

## 🙏 ขอขอบคุณ

- [@f870103](https://github.com/f870103) — ให้ยืมบัญชี LINE สำหรับทดสอบ และค้นพบ URL สำหรับเปลี่ยนเส้นทางไปยังแอป LINE Pay
- [@uta-a](https://github.com/uta-a) — การแก้ไขการเข้าสู่ระบบด้วย Google, รายการ Agent i บางส่วนของ "Hide Agent i buttons" และ "Show who read messages" ได้แนวคิดมาจาก [linimal](https://github.com/uta-a/linimal)
- [@SapitoSucio](https://github.com/SapitoSucio) — ฟีเจอร์และวิธีทำบางส่วนในที่นี้ได้แนวคิดมาจาก [FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches)
- [@SysAdminDoc](https://github.com/SysAdminDoc) — พบใน [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook) ว่าสตอรี่และรีลบางรายการที่ดาวน์โหลดไม่มีเสียง

## ⭐ ประวัติการติดดาว


<a href="https://www.star-history.com/?repos=andrewliang25%2Fmorphe-patches&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&theme=dark&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
 </picture>
</a>

## 📜 สัญญาอนุญาต

Andrew's Patches ใช้สัญญาอนุญาต [GNU General Public License v3.0](LICENSE)
