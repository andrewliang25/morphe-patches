# 🧩 Andrew's Patches

[English](README.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja-JP.md) | [ไทย](README.th-TH.md) | Bahasa Indonesia

Patch Morphe untuk LINE dan Facebook.

## ❓ Tentang

Ini adalah patch [Morphe](https://github.com/MorpheApp) untuk [LINE](https://line.me)
(`jp.naver.line.android`) dan Facebook (`com.facebook.katana`). Terapkan dengan Morphe CLI atau
Morphe Manager untuk membuat APK yang dimodifikasi.

> 💡 **Tidak mau membuatnya sendiri?** Unduh aplikasi yang sudah di-patch dari
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases).

> Proyek ini tidak memiliki hubungan dengan proyek sumber terbuka Morphe, LINE, LY Corporation,
> Facebook, atau Meta. Mereka tidak mendukung dan tidak menulis proyek ini.

## 🩹 Daftar patch

<!-- PATCHES_START EXPANDED -->
> **[v4.0.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v4.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;61 patches total
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;20 patches</summary>
<br>

**🎯 Supported versions:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | Stops Facebook watching for screenshots of ads and reporting which apps you install for ad attribution. |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | Stops Facebook downloading ads and its ad model in the background, which saves data, battery and storage. |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | Stops Facebook serving ads to other apps. Those apps then show their own ads or none, and rewarded ads can fail. |  |
| [[Feed] Block feed auto refresh](#feed-block-feed-auto-refresh) | Keeps your place in the news feed when you come back to Facebook. Pull down to refresh the feed. |  |
| [[Feed] Hide page suggestions and promo cards](#feed-hide-page-suggestions-and-promo-cards) | Removes the items that Facebook adds to the feed, such as "Pages you may like", promo cards and surveys. "Suggested for you" posts stay. |  |
| [[Feed] Hide post prompts](#feed-hide-post-prompts) | Removes the prompts Facebook adds inside a post, such as "Are you interested in this post?". |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | Removes sponsored posts from the news feed, with no gap left behind. |  |
| [[Fix] Restore screens on re-signed builds](#fix-restore-screens-on-re-signed-builds) | Makes profiles and some Settings pages open again on a re-signed build. A Root Mount install does not need this patch. |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | Makes Facebook's dark mode black instead of dark grey. Turn on dark mode in Facebook first. You can turn it off in Andrew's Patch Setting. |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | Adds "Andrew's Patch Setting" to the top of the Facebook menu. There you can turn some patches on or off without patching again, and see the credits and licenses. |  |
| [[General] Hide affiliate product links](#general-hide-affiliate-product-links) | Removes the product cards of affiliate shop links from Reels, feed posts and comments. The "Commission eligible" label stays. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | Opens web links in your default browser instead of Facebook's in-app browser. Facebook pages still open in the app. |  |
| [[Reels] Download any reel](#reels-download-any-reel) | Adds a Download button beside every reel. Videos save at the best quality the player streams. | • Save as H.264 |
| [[Reels] Hide interest prompts](#reels-hide-interest-prompts) | Removes the "Are you interested in this reel?" prompt from Reels. |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | Removes ads from Reels and Watch, including product banners over a reel and ads inside a video. |  |
| [[Reels] Picture-in-picture](#reels-picture-in-picture) | Keeps a reel playing in a small window when you leave Facebook from the Reels tab. Only vertical reels get a window. Needs Android 12 or later. |  |
| [[Stories] Disable auto advance](#stories-disable-auto-advance) | Keeps each story on the screen until you tap or swipe to the next one. |  |
| [[Stories] Download any story](#stories-download-any-story) | Adds Save to the menu of any story, including stories with music. Videos save at the best quality the player streams. | • Save as H.264 |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | Removes ad cards from the story viewer, so swiping through stories only shows stories people posted. |  |
| [[Stories] View stories anonymously](#stories-view-stories-anonymously) | Stops Facebook telling the server which stories you saw, so you are not in the viewer list. Stories that you saw still show as seen on this device. |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;41 patches</summary>
<br>

**🎯 Supported versions:**

| 26.16.0 | 26.17.1 | 26.18.2 |
| :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | Hides LINE display ads: the LINE Ads banners, the Smart Channel banner in the chat list and Google AdManager ads. |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | LINE no longer loads the Smart Channel banner ads. This patch makes the getBanners and getPrefetchableBanners responses null. |  |
| [[Call] Auto-record calls](#call-auto-record-calls) | Starts recording a 1-to-1 LINE call as soon as it connects. LINE saves the recording. Turn on call recording in Settings > LINE Labs first, because LINE does not record while that switch is off. Recording a call without the other person's consent is illegal in some places. You are responsible for following the law where you are. |  |
| [[Call] Record calls without notifying](#call-record-calls-without-notifying) | Records a 1-to-1 LINE call without telling the other party: no on-screen banner and no spoken recording announcement. Recording a call without the other person's consent is illegal in some places. You are responsible for following the law where you are. |  |
| [[Call] Remove recording time limit](#call-remove-recording-time-limit) | Records a call until it ends. LINE stops voice recordings after 60 minutes and video recordings after 20 or 50 minutes. |  |
| [[Call] Save recordings to device](#call-save-recordings-to-device) | Saves each call recording to the Movies/LINE folder on your phone instead of sharing it to Keep Memo, which refuses files over 1 GB. |  |
| [[Chat] Hide AI Friends button](#chat-hide-ai-friends-button) | Removes the AI Friends button from the Chats tab header. The Album button shows in its place. |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | Removes the "Events" row from the slide-out menu in a chat room. Events is a different feature from LINE Calendar, and it opens a server-hosted page. |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | Removes the LINE GIFT tile from the + attach menu in a chat room. |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | Removes the Transfer (LINE Pay) tile from the + attach menu in a chat room. |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | Removes the server-provided tools (Poll, Schedule and more) from the + menu in a chat. Built-in tiles stay. |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | Removes every LINE Calendar button: in the Chats tab, the chat toolbar and menus, and the Home tab. |  |
| [[Chat] Hide community button](#chat-hide-community-button) | Removes the community (OpenChat) button from the top of the Chats tab header. |  |
| [[Chat] Hide tips under messages](#chat-hide-tips-under-messages) | Hides the "CHECK" tips under some messages, such as a received video. |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | When you open a chat, LINE does not mark it read or send a read receipt. "Mark as read" still works. |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | Keeps messages that others unsend in 1:1 and group chats, with a red "Unsent" label next to the time. OpenChat is not covered. |  |
| [[Fix] Restore Google login via MicroG-RE](#fix-restore-google-login-via-microg-re) | Makes "Continue with Google" work on a re-signed build, through MicroG-RE. Root Mount does not need it. |  |
| [[Fix] Restore biometric login](#fix-restore-biometric-login) | Biometric login can be linked again on a re-signed build. A Root Mount install does not need this patch. |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | Makes Google Drive chat backup and restore work on a re-signed build, through MicroG-RE. Root Mount does not need it. |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | Shows maps again on a re-signed build, with OpenFreeMap tiles. It needs MicroG-RE 7.0.0 or later. Root Mount does not need it. |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | When LINE is fully closed, push notifications work again on a re-signed build. LINE also gets its Firebase Remote Config again. A Root Mount install does not need this patch. |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | Adds "Andrew's Patch Setting" to LINE Settings, below "Profile". There you can turn some patches on or off without patching again, and see the credits and licenses. |  |
| [[General] Block analytics](#general-block-analytics) | Stops LINE from sending usage events to its analytics service, such as the screens you open and the buttons you tap. Crash reports are not changed. |  |
| [[General] Custom font](#general-custom-font) | Adds a button to LINE's Font page to use a font file from your device. The page then no longer loads LINE's font list from the server. |  |
| [[General] Disable VOOM](#general-disable-voom) | VOOM deep links, shares, and notifications do nothing. If you open the standalone VOOM feed, it closes. Messaging and the other tabs do not change. |  |
| [[General] Enable themes on sub devices](#general-enable-themes-on-sub-devices) | Lets a sub device (a tablet or second phone logged in to the same account) use themes and the Black theme in dark mode. Hides the Theme Shop button there, because LINE does not open the shop on a sub device. |  |
| [[General] Hide Agent i buttons](#general-hide-agent-i-buttons) | Removes Agent i from the Home header, the search bar, the Wallet tab header and Settings, and hides Agent i in chats permanently, with its "Show Agent i in chat" switch in the + menu. |  |
| [[General] Hide LINE AI image editing](#general-hide-line-ai-image-editing) | Removes the LINE AI image editing buttons: "AI Edit" in the message long-press menu, the button next to an image message, and the button in the chat, album, Note and profile photo viewers. The normal photo editor stays. |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | Hides the green dots and N badges that mark new items, on header buttons, tabs, menus, lists and settings rows. Unread message counts do not change. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | Opens web links in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE links stay in LINE. |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | Opens LINE Pay in the standalone LINE Pay app, so the integrity check that fails on a re-signed build never runs. |  |
| [[General] Trust user-installed CAs](#general-trust-user-installed-cas) | Makes LINE trust certificate authorities you install, so your own HTTPS proxy can read LIFF and mini-app web traffic. Bank and LINE Pay stay pinned. For debugging. Off by default. |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | Removes the content feed below the friends list on the Home tab: LINE NEWS, official account posts and rankings. |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | Hides the recommended content, hot-topics and ad modules on the Home tab. |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | Hides all LINE Premium upsells, badges and pages. It unlocks nothing, because the server enforces Premium. |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | Removes the Premium unsend upsells that "Disable LINE Premium" leaves. Ordinary unsend still works. |  |
| [[Premium] Unlock app icons](#premium-unlock-app-icons) | Shows the App icon page in Settings, also with "Disable LINE Premium" on. It also lists the collaboration icons that are in the app but not offered now. You can apply every icon on the page, also the Premium ones. |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | Removes the LINE TODAY (News) tab from the main bottom navigation, in both the news-tab and news-row layouts. |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | Removes the Shopping tab from the main bottom navigation. This includes the Japan variant (Shopping, ショッピング) and the Taiwan variant (Discover, 逛逛). |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | Removes the VOOM (formerly Timeline) tab from the main bottom navigation. |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | Removes the Wallet (LINE Pay) tab from the main bottom navigation, in both the normal and mini-tab layouts. |  |

</details>

<!-- PATCHES_END -->

#### Cara memakai patch ini

Klik di sini untuk menambahkan patch ini ke Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

Atau tambahkan URL repositori ini sebagai sumber patch di Morphe: https://github.com/andrewliang25/morphe-patches

#### Mengubah patch di aplikasi

Dengan patch "[General] Andrew's Patch Setting", Anda bisa menyalakan atau mematikan sebagian besar
patch tanpa menerapkan patch lagi. Di LINE, buka **Pengaturan**, lalu **Andrew's Patch Setting** di
bawah **Profil**. Di Facebook, buka **Menu** (☰), lalu **Andrew's Patch Setting** di bagian atas.

Beberapa perubahan baru muncul setelah aplikasi dimulai ulang. Layar lalu menampilkan tombol untuk
memulai ulang aplikasi. Patch yang tidak Anda masukkan ke build tidak punya sakelar.

## 🛠️ Membuat build

Untuk membuat build Andrew's Patches, ikuti petunjuk di
[dokumentasi Morphe](https://github.com/MorpheApp/morphe-documentation).

## ⚠️ Batasan yang diketahui

### LINE: beberapa merchant tidak menerima LINE Pay

**Masalah:** Di beberapa merchant, pembayaran LINE Pay membuka aplikasi **LINE Pay** terpisah, yang
menampilkan error ([#161](https://github.com/andrewliang25/morphe-patches/issues/161), [#220](https://github.com/andrewliang25/morphe-patches/issues/220)).

**Penyebab:** *[General] Redirect LINE Pay* mengirim pembayaran ke aplikasi terpisah, karena LINE Pay
di dalam LINE yang dimodifikasi gagal dalam pemeriksaan integritas. Beberapa merchant hanya menerima
pembayaran dari dalam LINE. Patch tidak mengubah kode pembayaran, karena itu berisiko membuat akun
diblokir.

**Solusi sementara:** bayar dengan cara lain di merchant tersebut.

### LINE: passkey Google tidak berfungsi (build yang ditandatangani ulang)

**Masalah:** Di build yang di-patch (Standard), Anda tidak bisa masuk dengan passkey atau membuat
passkey ([#256](https://github.com/andrewliang25/morphe-patches/issues/256)).

**Penyebab:** Layanan Google Play hanya mengizinkan passkey untuk tanda tangan asli LINE. Patch tidak
bisa mengubah ini, dan MicroG-RE tidak membantu.

**Solusi sementara:** pasang dengan **Root Mount**, atau masuk dengan nomor telepon dan kata sandi,
atau dengan kode QR.

### Facebook: aplikasi Meta lain tidak bisa dipasang (build yang ditandatangani ulang)

**Masalah:** Jika **Facebook** yang di-patch terpasang, **Facebook Lite** dan **Messenger** gagal
dipasang dengan `INSTALL_FAILED_DUPLICATE_PERMISSION`.

**Penyebab:** Aplikasi Meta memakai nama izin yang sama, dan Android hanya mengizinkannya jika
aplikasi punya tanda tangan yang sama. Build yang di-patch ditandatangani ulang. Konflik yang sama
terjadi di [patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict).

**Solusi sementara:** pasang dengan **Root Mount**, atau simpan hanya salah satu dari dua aplikasi itu.

## 🙏 Terima kasih

- [@f870103](https://github.com/f870103) — meminjamkan akun LINE untuk pengujian, dan menemukan URL pengalihan aplikasi LINE Pay.
- [@uta-a](https://github.com/uta-a) — perbaikan login Google dan beberapa entri Agent i di "Hide Agent i buttons" mengambil ide dari [linimal](https://github.com/uta-a/linimal).
- [@SapitoSucio](https://github.com/SapitoSucio) — beberapa fitur dan implementasi di sini mengambil ide dari [FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches).
- [@SysAdminDoc](https://github.com/SysAdminDoc) — menemukan di [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook) bahwa beberapa unduhan story dan reel tersimpan tanpa suara.

## ⭐ Riwayat bintang


<a href="https://www.star-history.com/?repos=andrewliang25%2Fmorphe-patches&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&theme=dark&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
 </picture>
</a>

## 📜 Lisensi

Andrew's Patches dilisensikan di bawah [GNU General Public License v3.0](LICENSE).
