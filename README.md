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
> **[v1.8.0-dev.2](https://github.com/andrewliang25/morphe-patches/releases/tag/v1.8.0-dev.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;23 patches total
<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;23 patches</summary>
<br>

**🎯 Supported versions:**

| 26.11.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable LINE Premium](#disable-line-premium) | Hides all LINE Yahoo Premium (LYP) surfaces: the upsells, the badges, the Premium settings page, and the subscribe and manage flows. Premium chat backup changes to the ordinary chat-history backup. This patch unlocks nothing, because the server enforces premium. |  |
| [Disable VOOM](#disable-voom) | VOOM deep links, shares, and notifications do nothing. If you open the standalone VOOM feed, it closes. Messaging and the other tabs do not change. |  |
| [Fix chat backup sign-in via GmsCore](#fix-chat-backup-sign-in-via-gmscore) | Sends the Google account picker and the Drive token of chat-history backup through GmsCore. Backup and restore then work on a re-signed build. This patch needs MicroG-RE. It does not change how you sign in to a Google account. A Root Mount install does not need this patch. |  |
| [Fix push notifications](#fix-push-notifications) | When LINE is fully closed, push notifications work again on a re-signed build. A Root Mount install does not need this patch. |  |
| [Hide Events button](#hide-events-button) | Removes the "Events" row from the slide-out menu in a chat room. Events is a different feature from LINE Calendar, and it opens a server-hosted page. |  |
| [Hide Home content feed](#hide-home-content-feed) | Removes the content feed below the friends list on the Home tab. The feed shows LINE NEWS posts, official account posts, live cards, content units, and ranking units. The friends list, the service icons, and the other Home modules do not change. |  |
| [Hide Home modules](#hide-home-modules) | Hides clutter modules on the Home tab: the recommended stickers and content section, the real-time hot-topics (即時夯話題) block, and the ad modules. A separate patch hides the content feed below the friends list. |  |
| [Hide LINE GIFT button](#hide-line-gift-button) | Removes the LINE GIFT tile from the + attach menu in a chat room. |  |
| [Hide LINE TODAY tab](#hide-line-today-tab) | Removes the LINE TODAY (News) tab from the main bottom navigation, in both the news-tab and news-row layouts. |  |
| [Hide Shopping tab](#hide-shopping-tab) | Removes the Shopping tab from the main bottom navigation. This includes the Japan variant (Shopping, ショッピング) and the Taiwan variant (Discover, 逛逛). |  |
| [Hide Transfer button](#hide-transfer-button) | Removes the Transfer (LINE Pay) tile from the + attach menu in a chat room. |  |
| [Hide VOOM tab](#hide-voom-tab) | Removes the VOOM (formerly Timeline) tab from the main bottom navigation. |  |
| [Hide Wallet tab](#hide-wallet-tab) | Removes the Wallet (LINE Pay) tab from the main bottom navigation, in both the normal and mini-tab layouts. |  |
| [Hide ad views](#hide-ad-views) | Hides the LINE display ad views. These are the LINE Ads SDK containers in the whole app, the chat-list Smart Channel banner, and the Google AdManager ads. |  |
| [Hide attach menu extra tools](#hide-attach-menu-extra-tools) | Removes all the server-provided extra tools from the + attach menu in a chat room (Poll, Reservation, Schedule, Ladder shuffle, and more). The built-in tiles (camera, gallery, files, and contact) do not change. |  |
| [Hide calendar buttons](#hide-calendar-buttons) | Removes every LINE Calendar button inside the messenger. One is in the Chats-tab header. The other four are in a chat room: the top toolbar, the + attach menu, the slide-out chat menu, and the message long-press menu. |  |
| [Hide community button](#hide-community-button) | Removes the community (OpenChat) button from the top of the Chats tab header. |  |
| [Hide premium unsend upsells](#hide-premium-unsend-upsells) | Removes the LYP premium-unsend upsells that stay after "Disable LINE Premium". These are the "Unsend discreetly" button, the post-unsend promo link, and the expired-window unsend upsell. Ordinary unsend still works. |  |
| [Keep chats unread](#keep-chats-unread) | When you open a 1:1 or group chat, LINE does not mark it read and sends no read receipt. If you use "Mark as read" or "Mark all as read", LINE marks the chat read and sends the receipt. |  |
| [Keep unsent messages](#keep-unsent-messages) | Keeps unsent messages from 1:1 and group chats on your device instead of erasing them. This patch shows the usual "unsent a message" notice directly below the message that it kept. This patch does not apply to OpenChat. |  |
| [Open links in external browser](#open-links-in-external-browser) | When you tap a web link (http or https), it opens in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE deep links do not change. |  |
| [Redirect LINE Pay](#redirect-line-pay) | Opens LINE Pay flows in the standalone LINE Pay app instead of inside LINE. The device-integrity check that fails on a re-signed build never runs. Messaging does not change. |  |
| [Remove banner ads](#remove-banner-ads) | LINE no longer loads the Smart Channel banner ads. This patch makes the getBanners and getPrefetchableBanners responses null. |  |

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

This limitation does not affect chat-history backup. The *Fix chat backup sign-in via GmsCore*
patch restores it through [MicroG-RE](https://github.com/MorpheApp/MicroG-RE).

## 🙏 Special thanks

- [@f870103](https://github.com/f870103) — lent a LINE account for tests, and found the redirect URL of the LINE Pay app.

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
