# 🧩 Andrew's Patches

[English](README.md) | 繁體中文 | [日本語](README.ja-JP.md)

適用於 LINE 及其他我常用應用程式的 Morphe 修補程式。

## ❓ 關於

這是一套 [Morphe](https://github.com/MorpheApp) 修補程式，目前以
[LINE](https://line.me)（`jp.naver.line.android`）為主。請使用 Morphe CLI 或
Morphe Manager 套用，產生修改過的 APK。

> 💡 **不想自己建置？** 可以到
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases) 下載已修補的應用程式。

> 本專案與 Morphe 開源專案、LINE 及 LY Corporation 皆無關係。上述各方並未認可本專案，
> 也未參與撰寫。

## 🩹 修補程式清單

修補程式的名稱與說明由程式自動產生，因此以英文顯示。

<!-- PATCHES_START EXPANDED -->
> **[v3.13.0-dev.5](https://github.com/andrewliang25/morphe-patches/releases/tag/v3.13.0-dev.5)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;61 patches total
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

#### 如何使用這些修補程式

點這裡把這些修補程式加入 Morphe：https://morphe.software/add-source?github=andrewliang25/morphe-patches

或在 Morphe 中把這個儲存庫網址加為修補來源：https://github.com/andrewliang25/morphe-patches

#### 在應用程式內調整 LINE 修補程式

套用「[General] Andrew's Patch Setting」修補程式後，LINE 的**設定**頁面中，**個人檔案**下方會多出
**Andrew's Patch Setting** 一列。你可以在這裡開啟或關閉下列修補程式，不必重新修補：

- 保持聊天未讀，以及保留已收回的訊息
- 用外部瀏覽器開啟連結，以及停用 VOOM
- 隱藏 LINE TODAY、購物、VOOM 和錢包分頁。分頁的變更會在 LINE 重新啟動後生效。
- 隱藏聊天中的按鈕和提示：AI 好友、社群、行事曆、活動、LINE 禮物、轉帳、+ 選單中的額外工具，
  以及訊息下方的提示。其中部分變更會在 LINE 重新啟動後才生效。

建置時沒有加入的修補程式不會出現開關。這一列也會顯示作者與授權條款。

#### 在應用程式內調整 Facebook 修補程式

套用「[General] Andrew's Patch Setting」修補程式後，Facebook 的**選單**（☰）最上方會多出
**Andrew's Patch Setting** 一列。你可以在這裡開啟或關閉下列修補程式，不必重新修補：

- 動態消息：隱藏贊助貼文、粉絲專頁建議和發文提示，以及阻止動態消息自動重新整理
- Reels：隱藏贊助 Reels 和興趣提示、下載任何 Reels，以及子母畫面
- 限時動態：隱藏贊助限時動態、下載任何限時動態、匿名觀看限時動態，以及停用自動播放下一則
- 隱藏聯盟行銷商品連結，以及用外部瀏覽器開啟連結

部分變更會在 Facebook 重新啟動後才生效，畫面中有 **Restart Facebook** 一列可以重新啟動。
AMOLED 黑色主題沒有開關。建置時沒有加入的修補程式不會出現開關。

### 🛠️ 建置

建置 Andrew's Patches 的方法，請參考
[Morphe 文件](https://github.com/MorpheApp/morphe-documentation)。

## ⚠️ 已知限制

### LINE：部分商店不接受 LINE Pay

**狀況：**在部分商店，從網站或點餐系統發起的 LINE Pay 付款會失敗。LINE 開啟後，會轉到獨立的
**LINE Pay** 應用程式並顯示錯誤，例如「您必須透過 LINE 應用程式操作此功能」或「請更新 LINE」。

**原因：**LINE 內建的 LINE Pay 有完整性檢查，修改過的 LINE 無法通過。
*[General] Redirect LINE Pay* 修補程式會把每筆付款改送到獨立的 LINE Pay 應用程式。大多數商店都接受這種方式，
但部分商店只接受從 LINE 內部發起的付款，所以 LINE Pay 應用程式會拒絕。這些修補程式不會修改 LINE 中的
LINE Pay 部分，因為修改付款程式碼可能導致帳號被停用，也可能引起法律問題。因此這個問題不會修正
（[#161](https://github.com/andrewliang25/morphe-patches/issues/161)、
[#220](https://github.com/andrewliang25/morphe-patches/issues/220)）。

**替代方法：**在這些商店改用其他方式付款，例如到店付款。

### Facebook：無法安裝其他 Meta 應用程式（重新簽署的版本）

**狀況：**安裝已修補的 **Facebook** 後，使用相同權限名稱的官方 Meta 應用程式會無法安裝。目前已知的有
**Facebook Lite** 和 **Messenger**。安裝會停止並顯示 `INSTALL_FAILED_DUPLICATE_PERMISSION`。

**原因：**Meta 的應用程式會宣告相同的家族權限，例如
`com.facebook.permission.prod.FB_APP_COMMUNICATION`。除非兩個應用程式的簽署憑證相同，否則 Android
只允許一個應用程式擁有同一個權限名稱。已修補的版本經過重新簽署，憑證與 Meta 的憑證不同。安裝順序不會改變
這個結果。[patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict)
也有相同的衝突。

**替代方法：**用 **Root 掛載** 安裝已修補的 Facebook，這樣會保留原本的簽章。或者兩個應用程式只保留其中一個。

## 🙏 特別感謝

- [@f870103](https://github.com/f870103) — 借出 LINE 帳號供測試，並找到 LINE Pay 應用程式的轉址網址。
- [@uta-a](https://github.com/uta-a) — Google 登入修正，以及「Hide Agent i buttons」中部分 Agent i 項目的構想來自 [linimal](https://github.com/uta-a/linimal)。
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
