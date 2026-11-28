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

Deskripsi patch dibuat dan diterjemahkan secara otomatis. Nama patch tetap dalam bahasa Inggris.

<!-- PATCHES_START EXPANDED -->
> **[v4.2.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v4.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;Total 62 patch
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;20 patch</summary>
<br>

**🎯 Versi yang didukung:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Deskripsi | ⚙️&nbsp;Opsi |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | Menghentikan Facebook memantau tangkapan layar iklan dan melaporkan aplikasi apa yang Anda pasang untuk atribusi iklan. |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | Menghentikan Facebook mengunduh iklan dan model iklannya di latar belakang, sehingga hemat data, baterai, dan penyimpanan. |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | Menghentikan Facebook menayangkan iklan di aplikasi lain. Aplikasi itu lalu menampilkan iklannya sendiri atau tidak ada iklan, dan iklan berhadiah bisa gagal. |  |
| [[Feed] Block feed auto refresh](#feed-block-feed-auto-refresh) | Menjaga posisi Anda di umpan berita saat kembali ke Facebook. Tarik ke bawah untuk menyegarkan umpan. |  |
| [[Feed] Hide page suggestions and promo cards](#feed-hide-page-suggestions-and-promo-cards) | Menghapus item yang ditambahkan Facebook ke umpan, seperti "Laman yang mungkin Anda sukai", kartu promo, dan survei. Postingan "Disarankan untuk Anda" tetap ada. |  |
| [[Feed] Hide post prompts](#feed-hide-post-prompts) | Menghapus pertanyaan yang ditambahkan Facebook di dalam postingan, seperti "Apakah Anda tertarik dengan postingan ini?". |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | Menghapus postingan bersponsor dari umpan berita, tanpa meninggalkan celah. |  |
| [[Fix] Restore screens on re-signed builds](#fix-restore-screens-on-re-signed-builds) | Membuat profil dan beberapa halaman Pengaturan terbuka lagi di build yang ditandatangani ulang. Instalasi Root Mount tidak memerlukan patch ini. |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | Membuat mode gelap Facebook menjadi hitam, bukan abu-abu gelap. Aktifkan mode gelap di Facebook terlebih dahulu. Anda dapat mematikannya di Andrew's Patch Setting. |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | Menambahkan "Andrew's Patch Setting" di bagian atas menu Facebook. Di sana Anda dapat menyalakan atau mematikan beberapa patch tanpa patch ulang, serta melihat kredit dan lisensi. |  |
| [[General] Hide affiliate product links](#general-hide-affiliate-product-links) | Menghapus kartu produk dari tautan toko afiliasi di Reels, postingan umpan, dan komentar. Label "Memenuhi syarat komisi" tetap ada. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | Membuka tautan web di browser default Anda, bukan browser dalam aplikasi Facebook. Laman Facebook tetap terbuka di aplikasi. |  |
| [[Reels] Download any reel](#reels-download-any-reel) | Menambahkan tombol Unduh di samping setiap reel. Video disimpan dengan kualitas terbaik yang di-streaming pemutar. | • Simpan sebagai H.264 |
| [[Reels] Hide interest prompts](#reels-hide-interest-prompts) | Menghapus pertanyaan "Apakah Anda tertarik dengan reel ini?" dari Reels. |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | Menghapus iklan dari Reels dan Watch, termasuk banner produk di atas reel dan iklan di dalam video. |  |
| [[Reels] Picture-in-picture](#reels-picture-in-picture) | Menjaga reel tetap diputar di jendela kecil saat Anda meninggalkan Facebook dari tab Reels. Hanya reel vertikal yang mendapat jendela. Memerlukan Android 12 atau lebih baru. |  |
| [[Stories] Disable auto advance](#stories-disable-auto-advance) | Menjaga setiap cerita tetap di layar sampai Anda mengetuk atau menggeser ke cerita berikutnya. |  |
| [[Stories] Download any story](#stories-download-any-story) | Menambahkan Simpan ke menu cerita mana pun, termasuk cerita dengan musik. Video disimpan dengan kualitas terbaik yang di-streaming pemutar. | • Simpan sebagai H.264 |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | Menghapus kartu iklan dari penampil cerita, sehingga menggeser cerita hanya menampilkan cerita yang diposting orang. |  |
| [[Stories] View stories anonymously](#stories-view-stories-anonymously) | Menghentikan Facebook memberi tahu server cerita mana yang Anda lihat, sehingga Anda tidak ada di daftar penonton. Cerita yang sudah Anda lihat tetap tampil sebagai sudah dilihat di perangkat ini. |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;42 patch</summary>
<br>

**🎯 Versi yang didukung:**

| 26.16.0 | 26.17.1 | 26.18.2 | 26.19.0 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Deskripsi | ⚙️&nbsp;Opsi |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | Menyembunyikan iklan tampilan LINE: banner LINE Ads, banner Smart Channel di daftar obrolan, dan iklan Google AdManager. |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | Menghentikan LINE memuat iklan banner Smart Channel. |  |
| [[Call] Auto-record calls](#call-auto-record-calls) | Mulai merekam panggilan 1-ke-1 begitu tersambung. Aktifkan perekaman panggilan di Pengaturan > LINE Labs terlebih dahulu. Merekam tanpa persetujuan ilegal di beberapa tempat. |  |
| [[Call] Record calls without notifying](#call-record-calls-without-notifying) | Lawan bicara dalam panggilan 1-ke-1 tidak melihat banner perekaman dan tidak mendengar pengumuman. Merekam tanpa persetujuan ilegal di beberapa tempat. |  |
| [[Call] Remove recording time limit](#call-remove-recording-time-limit) | Merekam panggilan sampai berakhir. LINE menghentikan rekaman suara setelah 60 menit dan rekaman video setelah 20 atau 50 menit. |  |
| [[Call] Save recordings to device](#call-save-recordings-to-device) | Menyimpan setiap rekaman panggilan ke folder Movies/LINE di ponsel, bukan membagikannya ke Keep Memo yang menolak file di atas 1 GB. |  |
| [[Chat] Hide AI Friends button](#chat-hide-ai-friends-button) | Menghapus tombol AI Friends dari header tab Obrolan. Tombol Album tampil sebagai gantinya. |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | Menghapus baris "Acara" dari menu di ruang obrolan. |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | Menghapus kotak LINE GIFT dari menu lampiran + di ruang obrolan. |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | Menghapus kotak Transfer (LINE Pay) dari menu lampiran + di ruang obrolan. |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | Menghapus alat dari server (Polling, Jadwal, dan lainnya) dari menu + di obrolan. Kotak bawaan tetap ada. |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | Menghapus semua tombol LINE Calendar: di tab Obrolan, toolbar dan menu obrolan, serta tab Beranda. |  |
| [[Chat] Hide community button](#chat-hide-community-button) | Menghapus tombol komunitas (OpenChat) dari bagian atas header tab Obrolan. |  |
| [[Chat] Hide tips under messages](#chat-hide-tips-under-messages) | Menyembunyikan tips "CHECK" di bawah beberapa pesan, seperti video yang diterima. |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | Saat Anda membuka obrolan, LINE tidak menandainya sudah dibaca dan tidak mengirim tanda baca. "Tandai sudah dibaca" tetap berfungsi. |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | Menyimpan pesan yang dibatalkan orang lain di obrolan 1:1 dan grup, dengan label merah "Dibatalkan" di samping waktu. OpenChat tidak tercakup. |  |
| [[Chat] Show who read messages](#chat-show-who-read-messages) | Menambahkan "Dibaca oleh" ke menu tekan lama pada pesan di grup. Menu itu mencantumkan anggota yang membaca pesan. |  |
| [[Fix] Restore Google login via MicroG-RE](#fix-restore-google-login-via-microg-re) | Membuat "Lanjutkan dengan Google" berfungsi di build yang ditandatangani ulang, melalui MicroG-RE. Root Mount tidak memerlukannya. |  |
| [[Fix] Restore biometric login](#fix-restore-biometric-login) | Login biometrik dapat ditautkan lagi di build yang ditandatangani ulang. Instalasi Root Mount tidak memerlukan patch ini. |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | Membuat pencadangan dan pemulihan obrolan Google Drive berfungsi di build yang ditandatangani ulang, melalui MicroG-RE. Root Mount tidak memerlukannya. |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | Menampilkan peta lagi di build yang ditandatangani ulang, dengan ubin OpenFreeMap. Memerlukan MicroG-RE 7.0.0 atau lebih baru. Root Mount tidak memerlukannya. |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | Saat LINE ditutup sepenuhnya, notifikasi push berfungsi lagi di build yang ditandatangani ulang. LINE juga mendapat Firebase Remote Config lagi. Instalasi Root Mount tidak memerlukan patch ini. |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | Menambahkan "Andrew's Patch Setting" ke Pengaturan LINE, di bawah "Profil". Di sana Anda dapat menyalakan atau mematikan beberapa patch tanpa patch ulang, serta melihat kredit dan lisensi. |  |
| [[General] Block analytics](#general-block-analytics) | Menghentikan LINE mengirim peristiwa penggunaan ke layanan analitik, seperti layar yang Anda buka dan tombol yang Anda ketuk. Laporan crash tidak berubah. |  |
| [[General] Custom font](#general-custom-font) | Menambahkan tombol di halaman Font LINE untuk memakai file font dari perangkat Anda. Halaman itu lalu tidak lagi memuat daftar font LINE dari server. |  |
| [[General] Disable VOOM](#general-disable-voom) | Tautan dalam, bagikan, dan notifikasi VOOM tidak melakukan apa pun. Jika Anda membuka umpan VOOM mandiri, umpan itu menutup. Pesan dan tab lain tidak berubah. |  |
| [[General] Enable themes on sub devices](#general-enable-themes-on-sub-devices) | Memungkinkan perangkat sub (tablet atau ponsel kedua yang masuk ke akun yang sama) memakai tema dan tema Hitam di mode gelap. Menyembunyikan tombol Toko Tema di sana, karena LINE tidak membuka toko di perangkat sub. |  |
| [[General] Hide Agent i buttons](#general-hide-agent-i-buttons) | Menghapus Agent i dari header Beranda, bilah pencarian, header tab Dompet, dan Pengaturan, serta menyembunyikan Agent i di obrolan secara permanen, beserta sakelar "Tampilkan Agent i di obrolan" di menu +. |  |
| [[General] Hide LINE AI image editing](#general-hide-line-ai-image-editing) | Menghapus tombol edit gambar AI LINE: "AI Edit" di menu tekan lama pesan, tombol di samping pesan gambar, dan tombol di penampil foto obrolan, album, Catatan, dan foto profil. Editor foto biasa tetap ada. |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | Menyembunyikan titik hijau dan lencana N yang menandai item baru, di tombol header, tab, menu, daftar, dan baris pengaturan. Jumlah pesan belum dibaca tidak berubah. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | Membuka tautan web di browser default Anda, bukan browser dalam aplikasi LINE. Mini-app LIFF dan tautan LINE tetap di LINE. |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | Membuka LINE Pay di aplikasi LINE Pay mandiri, sehingga pemeriksaan integritas yang gagal di build yang ditandatangani ulang tidak pernah berjalan. |  |
| [[General] Trust user-installed CAs](#general-trust-user-installed-cas) | Membuat LINE memercayai otoritas sertifikat yang Anda pasang, sehingga proxy HTTPS Anda dapat membaca lalu lintas web LIFF dan mini-app. Bank dan LINE Pay tetap di-pin. Untuk debugging. Nonaktif secara default. |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | Menghapus umpan konten di bawah daftar teman pada tab Beranda: LINE NEWS, postingan akun resmi, dan peringkat. |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | Menyembunyikan modul konten rekomendasi, topik hangat, dan iklan di tab Beranda. |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | Menyembunyikan semua promosi, lencana, dan halaman LINE Premium. Patch ini tidak membuka apa pun, karena server yang menegakkan Premium. |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | Menghapus promosi Premium untuk batal kirim yang tersisa dari "Disable LINE Premium". Batal kirim biasa tetap berfungsi. |  |
| [[Premium] Unlock app icons](#premium-unlock-app-icons) | Memungkinkan Anda memakai semua ikon aplikasi, termasuk ikon Premium dan ikon kolaborasi lama. Halaman Ikon aplikasi tetap ada di Pengaturan meskipun "Disable LINE Premium" aktif. |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | Menghapus tab LINE TODAY (Berita) dari bilah bawah. |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | Menghapus tab Belanja dari bilah bawah, termasuk Discover (逛逛) di Taiwan. |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | Menghapus tab VOOM (dulu Linimasa) dari navigasi bawah utama. |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | Menghapus tab Dompet (LINE Pay) dari bilah bawah. |  |

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
- [@uta-a](https://github.com/uta-a) — perbaikan login Google, beberapa entri Agent i di "Hide Agent i buttons", dan "Show who read messages" mengambil ide dari [linimal](https://github.com/uta-a/linimal).
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
