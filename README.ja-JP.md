# 🧩 Andrew's Patches

[English](README.md) | [繁體中文](README.zh-TW.md) | 日本語 | [ไทย](README.th-TH.md) | [Bahasa Indonesia](README.id-ID.md)

LINE と Facebook 向けの Morphe パッチです。

## ❓ 概要

これは [LINE](https://line.me)（`jp.naver.line.android`）と Facebook（`com.facebook.katana`）向けの
[Morphe](https://github.com/MorpheApp) パッチです。Morphe CLI または
Morphe Manager でパッチを適用すると、改変した APK をビルドできます。

> 💡 **自分でビルドしたくない場合は？**
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases) からパッチ適用済みのアプリをダウンロードできます。

> このプロジェクトは、Morphe オープンソースプロジェクト、LINE、LY Corporation、Facebook、Meta とは関係ありません。
> これらの団体はこのプロジェクトを推奨しておらず、作成にも関わっていません。

## 🩹 パッチ一覧

パッチの説明は自動で生成され、翻訳されます。パッチの名前は英語のままです。

<!-- PATCHES_START EXPANDED -->
> **[v4.2.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v4.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;合計 62 件のパッチ
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;20 件のパッチ</summary>
<br>

**🎯 対応バージョン:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;パッチ | 📜&nbsp;説明 | ⚙️&nbsp;オプション |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | Facebook が広告のスクリーンショットを監視することと、広告の効果測定のためにインストール済みアプリを報告することを止めます。 |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | Facebook がバックグラウンドで広告と広告モデルをダウンロードするのを止めます。データ通信量、バッテリー、ストレージを節約できます。 |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | Facebook が他のアプリに広告を配信するのを止めます。それらのアプリは自分の広告を表示するか、広告なしになります。リワード広告は失敗することがあります。 |  |
| [[Feed] Block feed auto refresh](#feed-block-feed-auto-refresh) | Facebook に戻ったとき、ニュースフィードの位置を保ちます。下に引いてフィードを更新します。 |  |
| [[Feed] Hide page suggestions and promo cards](#feed-hide-page-suggestions-and-promo-cards) | Facebook がフィードに追加する項目を削除します。「おすすめのページ」、プロモーションカード、アンケートなどです。「おすすめの投稿」は残ります。 |  |
| [[Feed] Hide post prompts](#feed-hide-post-prompts) | Facebook が投稿の中に追加するプロンプトを削除します。「この投稿に興味がありますか?」などです。 |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | ニュースフィードからスポンサー投稿を、空白を残さずに削除します。 |  |
| [[Fix] Restore screens on re-signed builds](#fix-restore-screens-on-re-signed-builds) | 再署名したビルドで、プロフィールと一部の「設定」ページを再び開けるようにします。Root Mount でのインストールにはこのパッチは不要です。 |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | Facebook のダークモードを濃いグレーではなく黒にします。先に Facebook でダークモードをオンにしてください。「Andrew's Patch Setting」でオフにできます。 |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | Facebook のメニューの上部に「Andrew's Patch Setting」を追加します。ここで、パッチを再適用せずに一部のパッチをオン/オフでき、クレジットとライセンスも確認できます。 |  |
| [[General] Hide affiliate product links](#general-hide-affiliate-product-links) | リール、フィード投稿、コメントから、アフィリエイトショップリンクの商品カードを削除します。「コミッション対象」ラベルは残ります。 |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | ウェブリンクを Facebook のアプリ内ブラウザではなく、既定のブラウザで開きます。Facebook のページは引き続きアプリ内で開きます。 |  |
| [[Reels] Download any reel](#reels-download-any-reel) | すべてのリールの横にダウンロードボタンを追加します。動画は、プレーヤーがストリーミングする最高画質で保存されます。 | • H.264 で保存 |
| [[Reels] Hide interest prompts](#reels-hide-interest-prompts) | リールから「このリールに興味がありますか?」のプロンプトを削除します。 |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | リールと Watch から広告を削除します。リール上の商品バナーや動画内の広告も含みます。 |  |
| [[Reels] Picture-in-picture](#reels-picture-in-picture) | リールタブから Facebook を離れたとき、リールを小さなウィンドウで再生し続けます。縦型のリールだけがウィンドウになります。Android 12 以降が必要です。 |  |
| [[Stories] Disable auto advance](#stories-disable-auto-advance) | ストーリーを、タップまたはスワイプで次へ進むまで画面に表示し続けます。 |  |
| [[Stories] Download any story](#stories-download-any-story) | 音楽付きのストーリーを含む、すべてのストーリーのメニューに「保存」を追加します。動画は、プレーヤーがストリーミングする最高画質で保存されます。 | • H.264 で保存 |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | ストーリービューアから広告カードを削除します。スワイプすると、人が投稿したストーリーだけが表示されます。 |  |
| [[Stories] View stories anonymously](#stories-view-stories-anonymously) | どのストーリーを見たかを Facebook がサーバーに伝えるのを止めるので、閲覧者リストに表示されません。見たストーリーは、この端末では既読として表示されます。 |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;42 件のパッチ</summary>
<br>

**🎯 対応バージョン:**

| 26.16.0 | 26.17.1 | 26.18.2 | 26.19.0 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;パッチ | 📜&nbsp;説明 | ⚙️&nbsp;オプション |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | LINE のディスプレイ広告を非表示にします。LINE Ads バナー、トーク一覧の Smart Channel バナー、Google AdManager 広告が対象です。 |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | LINE が Smart Channel のバナー広告を読み込まないようにします。 |  |
| [[Call] Auto-record calls](#call-auto-record-calls) | 1対1の通話がつながると同時に録音を開始します。先に「設定」>「LINE Labs」で通話録音をオンにしてください。同意のない録音は違法となる地域があります。 |  |
| [[Call] Record calls without notifying](#call-record-calls-without-notifying) | 1対1の通話で、相手に録音バナーは表示されず、アナウンスも聞こえません。同意のない録音は違法となる地域があります。 |  |
| [[Call] Remove recording time limit](#call-remove-recording-time-limit) | 通話が終わるまで録音します。LINE は音声録音を60分、ビデオ録音を20分または50分で止めます。 |  |
| [[Call] Save recordings to device](#call-save-recordings-to-device) | 通話の録音を、Keep メモに共有する代わりに、スマートフォンの Movies/LINE フォルダに保存します。Keep メモは1 GB を超えるファイルを受け付けません。 |  |
| [[Chat] Hide AI Friends button](#chat-hide-ai-friends-button) | トークタブのヘッダーから AI フレンドボタンを削除します。代わりにアルバムボタンが表示されます。 |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | トークルームのメニューから「イベント」の行を削除します。 |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | トークルームの「+」添付メニューから LINE GIFT のタイルを削除します。 |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | トークルームの「+」添付メニューから送金（LINE Pay）のタイルを削除します。 |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | トークルームの「+」メニューから、サーバーが提供するツール（投票、スケジュールなど）を削除します。内蔵のタイルは残ります。 |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | すべての LINE カレンダーボタンを削除します。トークタブ、トークのツールバーとメニュー、ホームタブが対象です。 |  |
| [[Chat] Hide community button](#chat-hide-community-button) | トークタブのヘッダー上部からオープンチャットのボタンを削除します。 |  |
| [[Chat] Hide tips under messages](#chat-hide-tips-under-messages) | 受信した動画など、一部のメッセージの下に出る「CHECK」のヒントを非表示にします。 |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | トークを開いても、LINE は既読にせず、既読通知も送りません。「既読にする」は引き続き使えます。 |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | 1:1 とグループのトークで、相手が送信取消したメッセージを残し、時刻の横に赤い「送信取消」ラベルを表示します。オープンチャットは対象外です。 |  |
| [[Chat] Show who read messages](#chat-show-who-read-messages) | グループのメッセージの長押しメニューに「既読」を追加します。メッセージを読んだメンバーを一覧で表示します。 |  |
| [[Fix] Restore Google login via MicroG-RE](#fix-restore-google-login-via-microg-re) | 再署名したビルドで、MicroG-RE を通して「Google で続行」を使えるようにします。Root Mount では不要です。 |  |
| [[Fix] Restore biometric login](#fix-restore-biometric-login) | 再署名したビルドで、生体認証ログインを再び連携できるようにします。Root Mount でのインストールにはこのパッチは不要です。 |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | 再署名したビルドで、MicroG-RE を通して Google ドライブのトークバックアップと復元を使えるようにします。Root Mount では不要です。 |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | 再署名したビルドで、OpenFreeMap のタイルを使って地図を再び表示します。MicroG-RE 7.0.0 以降が必要です。Root Mount では不要です。 |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | LINE を完全に終了しているとき、再署名したビルドでプッシュ通知が再び動作します。LINE は Firebase Remote Config も再び取得できます。Root Mount でのインストールにはこのパッチは不要です。 |  |
| [[General] Andrew's Patch Setting](#general-andrew-s-patch-setting) | LINE の「設定」の「プロフィール」の下に「Andrew's Patch Setting」を追加します。ここで、パッチを再適用せずに一部のパッチをオン/オフでき、クレジットとライセンスも確認できます。 |  |
| [[General] Block analytics](#general-block-analytics) | LINE が分析サービスに利用イベント(開いた画面やタップしたボタンなど)を送るのを止めます。クラッシュレポートは変わりません。 |  |
| [[General] Custom font](#general-custom-font) | LINE のフォントページに、端末のフォントファイルを使うボタンを追加します。このページは LINE のフォント一覧をサーバーから読み込まなくなります。 |  |
| [[General] Disable VOOM](#general-disable-voom) | VOOM のディープリンク、共有、通知は何も起きません。単独の VOOM フィードを開くと閉じます。メッセージと他のタブは変わりません。 |  |
| [[General] Enable themes on sub devices](#general-enable-themes-on-sub-devices) | サブ端末(同じアカウントでログインしたタブレットや2台目のスマートフォン)でテーマと、ダークモードのブラックテーマを使えるようにします。LINE はサブ端末でショップを開かないため、そこでは着せかえショップのボタンを非表示にします。 |  |
| [[General] Hide Agent i buttons](#general-hide-agent-i-buttons) | ホームのヘッダー、検索バー、ウォレットタブのヘッダー、設定から Agent i を削除し、トーク内の Agent i を常に非表示にします。「+」メニューの「トークで Agent i を表示」スイッチも含みます。 |  |
| [[General] Hide LINE AI image editing](#general-hide-line-ai-image-editing) | LINE の AI 画像編集ボタンを削除します。メッセージの長押しメニューの「AI 編集」、画像メッセージの横のボタン、トーク、アルバム、ノート、プロフィール写真のビューアのボタンが対象です。通常の写真エディタは残ります。 |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | 新しい項目を示す緑のドットと N バッジを、ヘッダーボタン、タブ、メニュー、リスト、設定行で非表示にします。未読メッセージ数は変わりません。 |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | ウェブリンクを LINE のアプリ内ブラウザではなく、既定のブラウザで開きます。LIFF ミニアプリと LINE のリンクは LINE 内に残ります。 |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | LINE Pay を単独の LINE Pay アプリで開きます。そのため、再署名したビルドで失敗する整合性チェックは実行されません。 |  |
| [[General] Trust user-installed CAs](#general-trust-user-installed-cas) | 自分でインストールした認証局を LINE が信頼するようにし、自分の HTTPS プロキシで LIFF とミニアプリのウェブ通信を読めるようにします。銀行と LINE Pay は固定されたままです。デバッグ用です。初期設定はオフです。 |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | ホームタブの友だちリストの下にあるコンテンツフィードを削除します。LINE NEWS、公式アカウントの投稿、ランキングが対象です。 |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | ホームタブのおすすめコンテンツ、話題のトピック、広告のモジュールを非表示にします。 |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | LINE プレミアムの案内、バッジ、ページをすべて非表示にします。プレミアムはサーバーが制御するため、何もロック解除しません。 |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | 「LINE プレミアムを無効にする」が残す、送信取消のプレミアム案内を削除します。通常の送信取消は引き続き使えます。 |  |
| [[Premium] Unlock app icons](#premium-unlock-app-icons) | プレミアムや過去のコラボアイコンを含む、すべてのアプリアイコンを使えるようにします。「LINE プレミアムを無効にする」がオンでも、アプリアイコンのページは設定に残ります。 |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | 下部バーから LINE TODAY(ニュース)タブを削除します。 |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | 下部バーからショッピングタブを削除します。台湾の逛逛(Discover)も含みます。 |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | 下部のメインナビゲーションから VOOM(旧タイムライン)タブを削除します。 |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | 下部バーからウォレット(LINE Pay)タブを削除します。 |  |

</details>

<!-- PATCHES_END -->

#### パッチの使い方

ここをクリックして、これらのパッチを Morphe に追加します：https://morphe.software/add-source?github=andrewliang25/morphe-patches

または、このリポジトリの URL を Morphe のパッチソースとして追加します：https://github.com/andrewliang25/morphe-patches

#### アプリ内でパッチを変更する

「[General] Andrew's Patch Setting」パッチを適用すると、もう一度パッチを適用しなくても、ほとんどのパッチを
オンまたはオフにできます。LINE では、**設定**を開き、**プロフィール**の下の **Andrew's Patch Setting** を
開きます。Facebook では、**メニュー**（☰）を開き、一番上の **Andrew's Patch Setting** を開きます。

一部の変更は、アプリの再起動後に反映されます。その場合、画面に再起動のボタンが表示されます。
ビルドに含めなかったパッチにはスイッチがありません。

## 🛠️ ビルド

Andrew's Patches をビルドするには、
[Morphe のドキュメント](https://github.com/MorpheApp/morphe-documentation)の手順に従ってください。

## ⚠️ 既知の制限

### LINE：一部の店舗で LINE Pay が使えない

**内容：**一部の店舗では、LINE Pay の支払いで単体の **LINE Pay** アプリが開き、エラーが表示されます
（[#161](https://github.com/andrewliang25/morphe-patches/issues/161)、[#220](https://github.com/andrewliang25/morphe-patches/issues/220)）。

**原因：**「[General] Redirect LINE Pay」は支払いを単体のアプリに送ります。改変された LINE の中の LINE Pay は
整合性チェックで失敗するためです。一部の店舗は LINE 内からの支払いだけを受け付けます。アカウント停止の
おそれがあるため、パッチは支払いのコードを変更しません。

**回避策：**これらの店舗では、別の方法で支払ってください。

### LINE：Google のパスキーが使えない（再署名したビルド）

**内容：**パッチ適用済み（Standard）のビルドでは、パスキーでのログインも、パスキーの作成もできません
（[#256](https://github.com/andrewliang25/morphe-patches/issues/256)）。

**原因：**Google Play 開発者サービスは、LINE の元の署名にだけパスキーを許可します。パッチでは変更できず、
MicroG-RE でも解決しません。

**回避策：**Root Mount でインストールするか、電話番号とパスワード、または QR コードでログインしてください。

### Facebook：他の Meta アプリがインストールできない（再署名したビルド）

**内容：**パッチ適用済みの **Facebook** があると、**Facebook Lite** と **Messenger** が
`INSTALL_FAILED_DUPLICATE_PERMISSION` でインストールできません。

**原因：**Meta のアプリは同じ権限名を使います。Android では、署名が同じアプリだけがこれを共有できます。
パッチ適用済みのビルドは再署名されています。[patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict) でも同じ競合が起きます。

**回避策：**root 権限マウントでインストールするか、2 つのアプリのうち 1 つだけを残してください。

## 🙏 謝辞

- [@f870103](https://github.com/f870103) — テスト用の LINE アカウントを貸してくれ、LINE Pay アプリのリダイレクト URL を見つけてくれました。
- [@uta-a](https://github.com/uta-a) — Google ログインの修正、「Hide Agent i buttons」の一部の Agent i 項目、および「Show who read messages」は [linimal](https://github.com/uta-a/linimal) のアイデアに基づいています。
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
