# 🧩 Andrew's Patches

[English](README.md) | [繁體中文](README.zh-TW.md) | 日本語

LINE と、私が使うその他のアプリ向けの Morphe パッチです。

## ❓ 概要

これは [Morphe](https://github.com/MorpheApp) のパッチです。現在は
[LINE](https://line.me)（`jp.naver.line.android`）が中心です。Morphe CLI または
Morphe Manager でパッチを適用すると、改変した APK をビルドできます。

> 💡 **自分でビルドしたくない場合は？**
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases) からパッチ適用済みのアプリをダウンロードできます。

> このプロジェクトは、Morphe オープンソースプロジェクト、LINE、LY Corporation とは関係ありません。
> これらの団体はこのプロジェクトを推奨しておらず、作成にも関わっていません。

## 🩹 パッチ一覧

パッチの名前と説明は自動で生成されるため、英語で表示されます。

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

#### パッチの使い方

ここをクリックして、これらのパッチを Morphe に追加します：https://morphe.software/add-source?github=andrewliang25/morphe-patches

または、このリポジトリの URL を Morphe のパッチソースとして追加します：https://github.com/andrewliang25/morphe-patches

#### アプリ内で LINE のパッチを変更する

「[General] Andrew's Patch Setting」パッチを適用すると、LINE の**設定**の**プロフィール**の下に
**Andrew's Patch Setting** の行が追加されます。ここで次のパッチをオンまたはオフにでき、もう一度パッチを適用する必要はありません：

- トークを未読のままにする、送信取消されたメッセージを残す
- リンクを外部ブラウザで開く、VOOM を無効にする
- LINE TODAY、ショッピング、VOOM、ウォレットのタブを非表示にする。タブの変更は LINE の再起動後に反映されます。
- トークのボタンとヒントを非表示にする：AI Friends、コミュニティ、カレンダー、イベント、LINE GIFT、送金、
  ＋メニューの追加ツール、メッセージの下のヒント。一部の変更は LINE の再起動後に反映されます。

ビルドに含めなかったパッチにはスイッチがありません。この行には作者とライセンスも表示されます。

#### アプリ内で Facebook のパッチを変更する

「[General] Andrew's Patch Setting」パッチを適用すると、Facebook の**メニュー**（☰）の一番上に
**Andrew's Patch Setting** の行が追加されます。ここで次のパッチをオンまたはオフにでき、もう一度パッチを適用する必要はありません：

- フィード：広告投稿、ページのおすすめ、投稿の促しを非表示にする、フィードの自動更新を止める
- リール：広告リールと興味のプロンプトを非表示にする、すべてのリールをダウンロードする、ピクチャー・イン・ピクチャー
- ストーリーズ：広告ストーリーを非表示にする、すべてのストーリーをダウンロードする、ストーリーを匿名で見る、
  自動で次に進む機能を無効にする
- アフィリエイト商品リンクを非表示にする、リンクを外部ブラウザで開く

一部の変更は Facebook の再起動後に反映されます。そのため、画面に **Restart Facebook** の行があります。
AMOLED ブラックテーマにはスイッチがありません。ビルドに含めなかったパッチにはスイッチがありません。

### 🛠️ ビルド

Andrew's Patches をビルドするには、
[Morphe のドキュメント](https://github.com/MorpheApp/morphe-documentation)の手順に従ってください。

## ⚠️ 既知の制限

### LINE：一部の店舗で LINE Pay が使えない

**内容：**一部の店舗では、ウェブサイトや注文システムからの LINE Pay の支払いが失敗します。LINE が開いた後、
単体の **LINE Pay** アプリが開き、エラーが表示されます。エラーの例は「この機能は LINE アプリから操作する必要があります」
や「LINE を更新してください」です。

**原因：**LINE 内の LINE Pay には整合性チェックがあり、改変された LINE では失敗します。
*[General] Redirect LINE Pay* パッチは、すべての支払いを単体の LINE Pay アプリに送ります。ほとんどの店舗はこれを
受け付けます。一部の店舗は LINE 内からの支払いだけを受け付けるため、LINE Pay アプリが拒否します。これらのパッチは
LINE の LINE Pay 部分を変更しません。支払いのコードを変更すると、アカウントが停止されたり、法的な問題が起きたりする
おそれがあるためです。そのため、この問題は修正しません
（[#161](https://github.com/andrewliang25/morphe-patches/issues/161)、
[#220](https://github.com/andrewliang25/morphe-patches/issues/220)）。

**回避策：**これらの店舗では、別の方法で支払ってください。たとえば店頭で支払います。

### Facebook：他の Meta アプリがインストールできない（再署名したビルド）

**内容：**パッチ適用済みの **Facebook** をインストールした後、同じ権限名を使う公式の Meta アプリがインストール
できなくなります。**Facebook Lite** と **Messenger** が既知の例です。インストーラーは
`INSTALL_FAILED_DUPLICATE_PERMISSION` で停止します。

**原因：**Meta のアプリは、同じファミリー権限を宣言します。たとえば
`com.facebook.permission.prod.FB_APP_COMMUNICATION` です。Android では、2 つのアプリの署名証明書が同じでない限り、
1 つの権限名を持てるアプリは 1 つだけです。パッチ適用済みのビルドは再署名されるため、証明書が Meta の証明書と
異なります。インストールの順番を変えても結果は同じです。
[patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict)
でも同じ競合が起きます。

**回避策：**パッチ適用済みの Facebook を **root 権限マウント**でインストールします。この方法では元の署名が残ります。
または、2 つのアプリのうち 1 つだけを残します。

## 🙏 謝辞

- [@f870103](https://github.com/f870103) — テスト用の LINE アカウントを貸してくれ、LINE Pay アプリのリダイレクト URL を見つけてくれました。
- [@uta-a](https://github.com/uta-a) — Google ログインの修正と、「Hide Agent i buttons」の一部の Agent i 項目は [linimal](https://github.com/uta-a/linimal) のアイデアに基づいています。
- [@SapitoSucio](https://github.com/SapitoSucio) — ここにある一部の機能と実装は [FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches) のアイデアに基づいています。
- [@SysAdminDoc](https://github.com/SysAdminDoc) — [Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook) で、一部のストーリーとリールのダウンロードに音声がないことを見つけてくれました。

## ⭐ スター履歴

<a href="https://www.star-history.com/?repos=andrewliang25%2Fmorphe-patches&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&theme=dark&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
 </picture>
</a>

## 📜 ライセンス

Andrew's Patches は [GNU General Public License v3.0](LICENSE) でライセンスされています。
