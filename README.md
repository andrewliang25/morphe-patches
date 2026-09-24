# 🧩 Andrew's Patches

Morphe patches for LINE and other apps I use.

## ❓ About

These are [Morphe](https://github.com/MorpheApp) patches. The current focus is
[LINE](https://line.me) (`jp.naver.line.android`). Apply them with the Morphe CLI or
Morphe Manager to build a modified APK.

> 💡 **Do not want to build it yourself?** Download ready-made patched apps from
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases).

> This project has no connection to the Morphe open source project, LINE, or LY
> Corporation. They do not endorse it and did not write it.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v3.2.0-dev.3](https://github.com/andrewliang25/morphe-patches/releases/tag/v3.2.0-dev.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;36 patches total
<details open>
<summary>📦 Facebook&nbsp;&nbsp;•&nbsp;&nbsp;11 patches</summary>
<br>

**🎯 Supported versions:**

| 577.0.0.50.72 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [[Ad] Block ad telemetry](#ad-block-ad-telemetry) | Stops Facebook watching for screenshots of ads and reporting which apps you install for ad attribution. |  |
| [[Ad] Block background ad prefetch](#ad-block-background-ad-prefetch) | Stops Facebook downloading ads, ad images and the on-device ad-ranking model in the background. Saves mobile data, battery and storage whether or not you also hide the ads themselves. |  |
| [[Ad] Disable Audience Network](#ad-disable-audience-network) | Stops the Facebook app acting as the ad server for other apps on your device. This is the one patch that changes how other apps behave rather than how Facebook behaves: apps that show Facebook ads fall back to their own ad sources or show nothing, and an app that makes you watch an ad for a reward may not be able to give you one. |  |
| [[Feed] Hide sponsored posts](#feed-hide-sponsored-posts) | Removes sponsored posts from the news feed. The ad is dropped before it reaches the feed list, so it leaves no gap and its impression is never logged. Covers both server-delivered ads and the ones the app ranks and inserts locally. |  |
| [[Feed] Hide suggested and promoted posts](#feed-hide-suggested-and-promoted-posts) | Removes feed units Facebook injects rather than units your friends and pages posted: "Pages you may like" and its variants, the in-feed upsell nags and surveys, and the ad-experiment slots. Paid ads are a separate patch. |  |
| [[General] AMOLED black theme](#general-amoled-black-theme) | Makes the dark mode of Facebook black instead of dark grey, which saves power on an OLED screen. Turn on dark mode in Facebook first. Dividers, borders, text, icons and the coloured banners do not change. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | A web link that you tap opens in your default browser, and not in the in-app browser of Facebook. The pages of Facebook still open in the app. Thus login and checkout still work. |  |
| [[Reels] Download any reel](#reels-download-any-reel) | Adds a Download button beside every reel, and not only the reels that you posted. It saves the video the player is streaming, at the best quality the player holds, to Movies/Facebook. |  |
| [[Reels] Hide sponsored reels](#reels-hide-sponsored-reels) | Removes ads from Reels and Watch, so scrolling only shows videos from creators. Also removes the sponsored product banners over a reel and the ads inside a video, such as mid-rolls. |  |
| [[Stories] Download any story](#stories-download-any-story) | Adds a save option to the menu of any story, and not only to the stories that you posted. It saves the picture or the video that the story shows, including a story with music, which Facebook's own save refuses. Files go to Pictures/Facebook or Movies/Facebook. |  |
| [[Stories] Hide sponsored stories](#stories-hide-sponsored-stories) | Removes ad cards from the story viewer, so swiping through stories only shows stories people posted. |  |

</details>

<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;25 patches</summary>
<br>

**🎯 Supported versions:**

| 26.14.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [[Ad] Hide ad views](#ad-hide-ad-views) | Hides the LINE display ad views. These are the LINE Ads SDK containers in the whole app, the chat-list Smart Channel banner, and the Google AdManager ads. |  |
| [[Ad] Remove banner ads](#ad-remove-banner-ads) | LINE no longer loads the Smart Channel banner ads. This patch makes the getBanners and getPrefetchableBanners responses null. |  |
| [[Chat] Hide Events button](#chat-hide-events-button) | Removes the "Events" row from the slide-out menu in a chat room. Events is a different feature from LINE Calendar, and it opens a server-hosted page. |  |
| [[Chat] Hide LINE GIFT button](#chat-hide-line-gift-button) | Removes the LINE GIFT tile from the + attach menu in a chat room. |  |
| [[Chat] Hide Transfer button](#chat-hide-transfer-button) | Removes the Transfer (LINE Pay) tile from the + attach menu in a chat room. |  |
| [[Chat] Hide attach menu extra tools](#chat-hide-attach-menu-extra-tools) | Removes all the server-provided extra tools from the + attach menu in a chat room (Poll, Reservation, Schedule, Ladder shuffle, and more). The built-in tiles (camera, gallery, files, and contact) do not change. |  |
| [[Chat] Hide calendar buttons](#chat-hide-calendar-buttons) | Removes every LINE Calendar surface inside the messenger. One is in the Chats-tab header. Four are in a chat room: the top toolbar, the + attach menu, the slide-out chat menu, and the message long-press menu. The last is the Calendar block in the Friends sub-tab of the Home tab. |  |
| [[Chat] Hide community button](#chat-hide-community-button) | Removes the community (OpenChat) button from the top of the Chats tab header. |  |
| [[Chat] Keep chats unread](#chat-keep-chats-unread) | When you open a 1:1 or group chat, LINE does not mark it read and sends no read receipt. If you use "Mark as read" or "Mark all as read", LINE marks the chat read and sends the receipt. |  |
| [[Chat] Keep unsent messages](#chat-keep-unsent-messages) | Keeps unsent messages from 1:1 and group chats on your device instead of erasing them. This patch shows the usual "unsent a message" notice directly below the message that it kept. This patch does not apply to OpenChat. |  |
| [[Fix] Restore chat backup sign-in via MicroG-RE](#fix-restore-chat-backup-sign-in-via-microg-re) | Sends the Google account picker and the Drive token of chat-history backup through MicroG-RE. Backup and restore then work on a re-signed build. It does not change how you sign in to a Google account. A Root Mount install does not need this patch. |  |
| [[Fix] Restore location maps via MicroG-RE](#fix-restore-location-maps-via-microg-re) | Shows a map again on the location screens of a re-signed build. This covers the location picker, the location messages in a chat, and the location posts. The tiles come from OpenFreeMap and do not look like Google Maps. This patch needs MicroG-RE 7.0.0 or later. A Root Mount install does not need this patch. |  |
| [[Fix] Restore push notifications](#fix-restore-push-notifications) | When LINE is fully closed, push notifications work again on a re-signed build. A Root Mount install does not need this patch. |  |
| [[General] Disable VOOM](#general-disable-voom) | VOOM deep links, shares, and notifications do nothing. If you open the standalone VOOM feed, it closes. Messaging and the other tabs do not change. |  |
| [[General] Hide new item badges](#general-hide-new-item-badges) | Hides the green dots and N badges that mark new items, on header buttons, tabs, menus, lists and settings rows. Unread message counts do not change. |  |
| [[General] Open links in external browser](#general-open-links-in-external-browser) | When you tap a web link (http or https), it opens in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE deep links do not change. |  |
| [[General] Redirect LINE Pay](#general-redirect-line-pay) | Opens LINE Pay flows in the standalone LINE Pay app instead of inside LINE. The device-integrity check that fails on a re-signed build never runs. Messaging does not change. |  |
| [[Home] Hide Home content feed](#home-hide-home-content-feed) | Removes the content feed below the friends list on the Home tab. The feed shows LINE NEWS posts, official account posts, live cards, content units, and ranking units. The friends list, the service icons, and the other Home modules do not change. |  |
| [[Home] Hide Home modules](#home-hide-home-modules) | Hides clutter modules on the Home tab: the recommended stickers and content section, the real-time hot-topics (即時夯話題) block, and the ad modules. A separate patch hides the content feed below the friends list. |  |
| [[Premium] Disable LINE Premium](#premium-disable-line-premium) | Hides all LINE Yahoo Premium (LYP) surfaces: the upsells, the badges, the Premium settings page, and the subscribe and manage flows. Premium chat backup changes to the ordinary chat-history backup. This patch unlocks nothing, because the server enforces premium. |  |
| [[Premium] Hide premium unsend upsells](#premium-hide-premium-unsend-upsells) | Removes the LYP premium-unsend upsells that stay after "Disable LINE Premium". These are the "Unsend discreetly" button, the post-unsend promo link, and the expired-window unsend upsell. Ordinary unsend still works. |  |
| [[Tab] Hide LINE TODAY tab](#tab-hide-line-today-tab) | Removes the LINE TODAY (News) tab from the main bottom navigation, in both the news-tab and news-row layouts. |  |
| [[Tab] Hide Shopping tab](#tab-hide-shopping-tab) | Removes the Shopping tab from the main bottom navigation. This includes the Japan variant (Shopping, ショッピング) and the Taiwan variant (Discover, 逛逛). |  |
| [[Tab] Hide VOOM tab](#tab-hide-voom-tab) | Removes the VOOM (formerly Timeline) tab from the main bottom navigation. |  |
| [[Tab] Hide Wallet tab](#tab-hide-wallet-tab) | Removes the Wallet (LINE Pay) tab from the main bottom navigation, in both the normal and mini-tab layouts. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

Or add this repository URL as a patch source in Morphe: https://github.com/andrewliang25/morphe-patches

### 🛠️ Building

To build Andrew's Patches, obey the instructions in the
[Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## ⚠️ Known limitations

### LINE: Google account sign-in fails (re-signed builds)

**What:** On a patched **LINE** build, you cannot sign in with a Google account or link one.

**Why:** Google accepts an account only for an OAuth client that is registered under LINE's package
name **and** its original signing certificate. A re-signed build changes that certificate, so no
client matches. LINE asks Android's Credential Manager for the account, so the *system* picks
Google Play Services. No patch can change this ([details](docs/line-patch-map.md)).

**Workaround:** install with **Root Mount**, which keeps LINE's original signature. Do not use the
**Standard** install.

This limitation does not affect chat-history backup. The *[Fix] Restore chat backup sign-in via MicroG-RE*
patch restores it through [MicroG-RE](https://github.com/MorpheApp/MicroG-RE).

### LINE: maps show an empty grid (re-signed builds)

**What:** On a patched **LINE** build, the maps on the location screens are empty. This covers the
location picker, the location messages in a chat, and the location posts. You can still send your
current location, and only the map is blank.

**Why:** Google draws a map only for an API key that is registered under LINE's package name **and**
its original signing certificate. A re-signed build changes that certificate. Google Play Services
reports the certificate from its own process, so no patch can correct it
([details](docs/line-patch-map.md)).

**Workaround:** enable the *[Fix] Restore location maps via MicroG-RE* patch. It draws the maps through
[MicroG-RE](https://github.com/MorpheApp/MicroG-RE) **7.0.0 or later** instead, which is the first
version with a map renderer. Google Play Services must still be installed. The tiles then come from OpenFreeMap,
so they do not look like Google Maps. There is no satellite view. A **Root Mount** install keeps
LINE's original signature and needs neither the patch nor MicroG-RE.

### Facebook: other Meta apps do not install (re-signed builds)

**What:** After you install a patched **Facebook** build, an official Meta app that shares the same
permission names does not install. **Facebook Lite** is the reported example. The installer stops
with `INSTALL_FAILED_DUPLICATE_PERMISSION`.

**Why:** Meta apps declare the same family permissions, for example
`com.facebook.permission.prod.FB_APP_COMMUNICATION`. Android permits only one owner of a permission
name, unless the two apps have the same signing certificate. A patched build is re-signed, so its
certificate is different from the certificate of Meta. The order of the installs does not change
this. The same conflict occurs in
[patched-apps](https://github.com/andrewliang25/patched-apps#meta-app-clones-duplicate-permission-conflict).

**Workaround:** install the patched Facebook with **Root Mount**, which keeps the original
signature. Or keep only one app of the two.

## 🙏 Special thanks

- [@f870103](https://github.com/f870103) — lent a LINE account for tests, and found the redirect URL of the LINE Pay app.
- [@SapitoSucio](https://github.com/SapitoSucio) — some features and implementations here take their idea from [FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches).

## ⭐ Star history

<a href="https://www.star-history.com/?repos=andrewliang25%2Fmorphe-patches&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&theme=dark&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=andrewliang25/morphe-patches&type=date&legend=top-left&sealed_token=bcBbvce_G1Ww6apQwWC74HNf7mWANZkAaRZC-jk1TFjdlCjsmb6zKtbPBByRyJB45-ZldU6RwekQ_WAKV_L62P8tuslW7Q6a39UdAaFAxan0cj5UX8WxgG3DwF68QJZq5GmmKaDE-njntBl3WJLxf6_jMvUI7Tq2ap83khC5cDb_Y12NL8p271Sk3fSN" />
 </picture>
</a>

## 📜 License

Andrew's Patches are licensed under the [GNU General Public License v3.0](LICENSE)
