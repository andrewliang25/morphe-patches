# 🧩 Andrew's Patches

[English](README.md) | 繁體中文 | [日本語](README.ja-JP.md) | [ไทย](README.th-TH.md) | [Bahasa Indonesia](README.id-ID.md)

適用於 LINE 和 Facebook 的 Morphe 修補程式。

## ❓ 關於

這是一套適用於 [LINE](https://line.me)（`jp.naver.line.android`）和 Facebook（`com.facebook.katana`）的
[Morphe](https://github.com/MorpheApp) 修補程式。請使用 Morphe CLI 或
Morphe Manager 套用，產生修改過的 APK。

> 💡 **不想自己建置？** 可以到
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases) 下載已修補的應用程式。

> 本專案與 Morphe 開源專案、LINE、LY Corporation、Facebook 及 Meta 皆無關係。上述各方並未認可本專案，
> 也未參與撰寫。

## 🩹 修補程式清單

修補程式的說明由程式自動產生並翻譯。修補程式的名稱維持英文。

<!-- PATCHES_START EXPANDED -->
> **[v4.2.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v4.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;共 62 個修補
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;20 個修補</summary>
<br>

**🎯 支援版本:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;修補 | 📜&nbsp;說明 | ⚙️&nbsp;選項 |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | 停止 Facebook 偵測廣告截圖，以及為了廣告歸因而回報你安裝了哪些應用程式。 |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | 停止 Facebook 在背景下載廣告及其廣告模型，可節省流量、電量與儲存空間。 |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | 停止 Facebook 向其他應用程式投放廣告。那些應用程式會改為顯示自己的廣告或不顯示廣告，獎勵廣告可能失敗。 |  |
| [[Feed] Block feed auto refresh](#feed-block-feed-auto-refresh) | 回到 Facebook 時保留你在動態消息中的位置。下拉即可重新整理動態。 |  |
| [[Feed] Hide page suggestions and promo cards](#feed-hide-page-suggestions-and-promo-cards) | 移除 Facebook 加入動態的項目，例如「你可能會喜歡的粉絲專頁」、推廣卡片與問卷。「為你推薦」貼文會保留。 |  |
| [[Feed] Hide post prompts](#feed-hide-post-prompts) | 移除 Facebook 在貼文內加入的提示，例如「你對這則貼文感興趣嗎？」。 |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | 移除動態消息中的贊助貼文，且不留空白。 |  |
| [[Fix] Restore screens on re-signed builds](#fix-restore-screens-on-re-signed-builds) | 在重新簽署的版本上，讓個人檔案與部分「設定」頁面可再次開啟。Root Mount 安裝不需要此修補。 |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | 讓 Facebook 的深色模式變成純黑而非深灰。請先在 Facebook 開啟深色模式。可在「Andrew's Patch Setting」中關閉。 |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | 在 Facebook 選單頂部新增「Andrew's Patch Setting」。你可以在此開關部分修補，無需重新修補，也能查看感謝名單與授權。 |  |
| [[General] Hide affiliate product links](#general-hide-affiliate-product-links) | 移除 Reels、動態貼文與留言中的聯盟行銷商品卡片。「符合佣金資格」標籤會保留。 |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | 用預設瀏覽器開啟網頁連結，而不是 Facebook 內建瀏覽器。Facebook 頁面仍在應用程式內開啟。 |  |
| [[Reels] Download any reel](#reels-download-any-reel) | 在每則 Reel 旁新增下載按鈕。影片以播放器串流的最高畫質儲存。 | • 儲存為 H.264 |
| [[Reels] Hide interest prompts](#reels-hide-interest-prompts) | 移除 Reels 中的「你對這則 Reel 感興趣嗎？」提示。 |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | 移除 Reels 與 Watch 中的廣告，包括 Reel 上的商品橫幅與影片內的廣告。 |  |
| [[Reels] Picture-in-picture](#reels-picture-in-picture) | 從 Reels 分頁離開 Facebook 時，讓 Reel 繼續在小視窗播放。只有直式 Reels 會有小視窗。需要 Android 12 或更新版本。 |  |
| [[Stories] Disable auto advance](#stories-disable-auto-advance) | 讓每則限時動態停留在畫面上，直到你點擊或滑動到下一則。 |  |
| [[Stories] Download any story](#stories-download-any-story) | 在任何限時動態的選單中新增「儲存」，包括含音樂的限時動態。影片以播放器串流的最高畫質儲存。 | • 儲存為 H.264 |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | 移除限時動態檢視器中的廣告卡片，滑動時只會看到他人發布的限時動態。 |  |
| [[Stories] View stories anonymously](#stories-view-stories-anonymously) | 阻止 Facebook 告知伺服器你看過哪些限時動態，因此你不會出現在觀看者名單中。你看過的限時動態在此裝置上仍顯示為已看過。 |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;42 個修補</summary>
<br>

**🎯 支援版本:**

| 26.16.0 | 26.17.1 | 26.18.2 | 26.19.0 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;修補 | 📜&nbsp;說明 | ⚙️&nbsp;選項 |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | 隱藏 LINE 的展示廣告：LINE Ads 橫幅、聊天列表中的 Smart Channel 橫幅，以及 Google AdManager 廣告。 |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | 阻止 LINE 載入 Smart Channel 橫幅廣告。 |  |
| [[Call] Auto-record calls](#call-auto-record-calls) | 1對1通話接通後立即開始錄音。請先在「設定」>「LINE Labs」開啟通話錄音。在某些地區，未經同意錄音是違法的。 |  |
| [[Call] Record calls without notifying](#call-record-calls-without-notifying) | 1對1通話中，對方不會看到錄音橫幅，也不會聽到語音提示。在某些地區，未經同意錄音是違法的。 |  |
| [[Call] Remove recording time limit](#call-remove-recording-time-limit) | 錄音直到通話結束。LINE 會在 60 分鐘後停止語音錄音，並在 20 或 50 分鐘後停止視訊錄音。 |  |
| [[Call] Save recordings to device](#call-save-recordings-to-device) | 將每段通話錄音儲存到手機的 Movies/LINE 資料夾，而不是分享到 Keep 記事本（Keep 不接受超過 1 GB 的檔案）。 |  |
| [[Chat] Hide AI Friends button](#chat-hide-ai-friends-button) | 移除聊天分頁頂部的 AI 好友按鈕，改為顯示相簿按鈕。 |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | 移除聊天室選單中的「活動」項目。 |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | 移除聊天室「+」附件選單中的 LINE GIFT 圖示。 |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | 移除聊天室「+」附件選單中的轉帳（LINE Pay）圖示。 |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | 移除聊天室「+」選單中由伺服器提供的工具（投票、行程等）。內建圖示會保留。 |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | 移除所有 LINE 行事曆按鈕：聊天分頁、聊天室工具列與選單，以及主頁分頁。 |  |
| [[Chat] Hide community button](#chat-hide-community-button) | 移除聊天分頁頂部的社群（OpenChat）按鈕。 |  |
| [[Chat] Hide tips under messages](#chat-hide-tips-under-messages) | 隱藏部分訊息（例如收到的影片）下方的「CHECK」提示。 |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | 開啟聊天室時，LINE 不會將其標示為已讀，也不會傳送已讀回條。「標示為已讀」仍可使用。 |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | 保留他人在 1對1 與群組聊天中收回的訊息，並在時間旁顯示紅色「已收回」標籤。不含 OpenChat。 |  |
| [[Chat] Show who read messages](#chat-show-who-read-messages) | 在群組訊息的長按選單中新增「已讀者」，列出已讀該訊息的成員。 |  |
| [[Fix] Restore Google login via MicroG-RE](#fix-restore-google-login-via-microg-re) | 在重新簽署的版本上，透過 MicroG-RE 讓「使用 Google 繼續」可用。Root Mount 不需要此修補。 |  |
| [[Fix] Restore biometric login](#fix-restore-biometric-login) | 在重新簽署的版本上，可再次綁定生物辨識登入。Root Mount 安裝不需要此修補。 |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | 在重新簽署的版本上，透過 MicroG-RE 讓 Google 雲端硬碟聊天備份與還原可用。Root Mount 不需要此修補。 |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | 在重新簽署的版本上，以 OpenFreeMap 圖磚重新顯示地圖。需要 MicroG-RE 7.0.0 或更新版本。Root Mount 不需要此修補。 |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | LINE 完全關閉時，在重新簽署的版本上推播通知會再次運作，LINE 也能重新取得 Firebase Remote Config。Root Mount 安裝不需要此修補。 |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | 在 LINE「設定」中、「個人檔案」下方新增「Andrew's Patch Setting」。你可以在此開關部分修補，無需重新修補，也能查看感謝名單與授權。 |  |
| [[General] Block analytics](#general-block-analytics) | 阻止 LINE 向分析服務傳送使用事件，例如你開啟的畫面與點擊的按鈕。當機報告不變。 |  |
| [[General] Custom font](#general-custom-font) | 在 LINE 的字型頁面新增按鈕，可使用裝置上的字型檔。此頁面之後不再從伺服器載入 LINE 的字型清單。 |  |
| [[General] Disable VOOM](#general-disable-voom) | VOOM 的深層連結、分享與通知都不會有作用。若開啟獨立的 VOOM 動態，它會關閉。訊息與其他分頁不受影響。 |  |
| [[General] Enable themes on sub devices](#general-enable-themes-on-sub-devices) | 讓子裝置（登入同一帳號的平板或第二支手機）可使用佈景主題，以及深色模式下的黑色主題。並隱藏該處的佈景主題商店按鈕，因為 LINE 不會在子裝置上開啟商店。 |  |
| [[General] Hide Agent i buttons](#general-hide-agent-i-buttons) | 移除主頁標題列、搜尋列、錢包分頁標題列與「設定」中的 Agent i，並永久隱藏聊天中的 Agent i（連同「+」選單中的「在聊天中顯示 Agent i」開關）。 |  |
| [[General] Hide LINE AI image editing](#general-hide-line-ai-image-editing) | 移除 LINE AI 圖片編輯按鈕：訊息長按選單中的「AI 編輯」、圖片訊息旁的按鈕，以及聊天、相簿、記事與大頭貼檢視器中的按鈕。一般相片編輯器會保留。 |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | 隱藏標示新項目的綠點與 N 標記，包括標題列按鈕、分頁、選單、清單與設定列。未讀訊息數不變。 |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | 用預設瀏覽器開啟網頁連結，而不是 LINE 內建瀏覽器。LIFF 迷你應用程式與 LINE 連結仍在 LINE 內開啟。 |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | 以獨立的 LINE Pay 應用程式開啟 LINE Pay，因此在重新簽署版本上會失敗的完整性檢查不會執行。 |  |
| [[General] Trust user-installed CAs](#general-trust-user-installed-cas) | 讓 LINE 信任你安裝的憑證授權單位，使你自己的 HTTPS 代理伺服器可讀取 LIFF 與迷你應用程式的網頁流量。銀行與 LINE Pay 仍固定憑證。供除錯使用。預設關閉。 |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | 移除主頁分頁好友列表下方的內容動態：LINE NEWS、官方帳號貼文與排行榜。 |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | 隱藏主頁分頁上的推薦內容、熱門話題與廣告模組。 |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | 隱藏所有 LINE Premium 的推銷、徽章與頁面。它不會解鎖任何功能，因為 Premium 由伺服器控管。 |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | 移除「停用 LINE Premium」所遺留的 Premium 收回訊息推銷。一般收回訊息仍可使用。 |  |
| [[Premium] Unlock app icons](#premium-unlock-app-icons) | 讓你使用所有應用程式圖示，包括 Premium 與舊的合作圖示。即使開啟「停用 LINE Premium」，「設定」中仍會保留應用程式圖示頁面。 |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | 移除底部列的 LINE TODAY（新聞）分頁。 |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | 移除底部列的購物分頁，包括台灣的逛逛。 |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | 移除主底部導覽列的 VOOM（前身為貼文串）分頁。 |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | 移除底部列的錢包（LINE Pay）分頁。 |  |

</details>

<!-- PATCHES_END -->

#### 如何使用這些修補程式

點這裡把這些修補程式加入 Morphe：https://morphe.software/add-source?github=andrewliang25/morphe-patches

或在 Morphe 中把這個儲存庫網址加為修補來源：https://github.com/andrewliang25/morphe-patches

#### 在應用程式內調整修補程式

套用「[General] Andrew's Patch Setting」修補程式後，大部分修補程式都可以直接開啟或關閉，不必重新修補。在 LINE 中，開啟**設定**，再點**個人檔案**下方的
**Andrew's Patch Setting**。在 Facebook 中，開啟**選單**（☰），再點最上方的 **Andrew's Patch Setting**。

部分變更會在應用程式重新啟動後才生效，畫面上會出現重新啟動的按鈕。建置時沒有加入的修補程式不會出現開關。

## 🛠️ 建置

建置 Andrew's Patches 的方法，請參考
[Morphe 文件](https://github.com/MorpheApp/morphe-documentation)。

## ⚠️ 已知限制

### LINE：部分商店不接受 LINE Pay

**狀況：**在部分商店，LINE Pay 付款會開啟獨立的 **LINE Pay** 應用程式並顯示錯誤（[#161](https://github.com/andrewliang25/morphe-patches/issues/161)、[#220](https://github.com/andrewliang25/morphe-patches/issues/220)）。

**原因：**「[General] Redirect LINE Pay」會把付款改送到獨立的應用程式，因為修改過的 LINE 內建的 LINE Pay
無法通過完整性檢查。部分商店只接受從 LINE 內部發起的付款。修補程式不會修改付款程式碼，因為這可能導致帳號被停用。

**替代方法：**在這些商店改用其他方式付款。

### LINE：無法使用 Google 通行金鑰（重新簽署的版本）

**狀況：**在套用修補程式的（Standard）版本上，無法用通行金鑰登入，也無法建立通行金鑰
（[#256](https://github.com/andrewliang25/morphe-patches/issues/256)）。

**原因：**Google Play 服務只允許 LINE 原本的簽署使用通行金鑰。修補程式無法改變這一點，MicroG-RE 也沒有幫助。

**替代方法：**改用 **Root Mount** 安裝，或用電話號碼和密碼、QR 碼登入。

### Facebook：無法安裝其他 Meta 應用程式（重新簽署的版本）

**狀況：**安裝已修補的 **Facebook** 後，**Facebook Lite** 和 **Messenger** 會因
`INSTALL_FAILED_DUPLICATE_PERMISSION` 無法安裝。

**原因：**Meta 的應用程式使用相同的權限名稱，Android 只允許簽署相同的應用程式共用。已修補的版本經過重新簽署。
[patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict) 也有相同的衝突。

**替代方法：**用 Root 掛載安裝，或兩個應用程式只保留其中一個。

## 🙏 特別感謝

- [@f870103](https://github.com/f870103) — 借出 LINE 帳號供測試，並找到 LINE Pay 應用程式的轉址網址。
- [@uta-a](https://github.com/uta-a) — Google 登入修正、「Hide Agent i buttons」中部分 Agent i 項目，以及「Show who read messages」的構想來自 [linimal](https://github.com/uta-a/linimal)。
- [@SapitoSucio](https://github.com/SapitoSucio) — 這裡的部分功能與實作構想來自 [FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches)。
- [@SysAdminDoc](https://github.com/SysAdminDoc) — 在 [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook) 中發現部分限時動態和 Reels 下載後沒有聲音。

## ⭐ Star 歷史

<a href="https://www.star-history.com/?repos=andrewliang25%2Fmorphe-patches&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&theme=dark&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
 </picture>
</a>

## 📜 授權條款

Andrew's Patches 採用 [GNU General Public License v3.0](LICENSE) 授權。
