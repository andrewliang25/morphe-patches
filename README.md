# 🧩 Andrew's Patches

Morphe patches for LINE and other apps I use.

## ❓ About

A collection of [Morphe](https://github.com/MorpheApp) patches, currently focused on
[LINE](https://line.me) (`jp.naver.line.android`). Apply them with the Morphe CLI or
Morphe Manager to build a modified APK.

> This is an independent project and is not affiliated with, endorsed by, or authored by
> the Morphe open source project, LINE, or LY Corporation.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0](https://github.com/andrewliang25/morphe-patches/releases/tag/v1.1.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;16 patches total
<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;16 patches</summary>
<br>

**🎯 Supported versions:**

| 26.11.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable LINE Pay](#disable-line-pay) | Closes any LINE Pay screen immediately on open, so Pay flows (and their device-integrity check) never run. Messaging is unaffected. |  |
| [Disable VOOM](#disable-voom) | Neutralizes VOOM entry points: VOOM deep links, shares, and notifications (line://home/*) do nothing, and the standalone VOOM feed closes on open. Also disables VOOM LIVE, the birthday board, and share-to-VOOM. Messaging, friend profiles, and other tabs are unaffected. |  |
| [Hide Events button](#hide-events-button) | Removes the "Events" row from a chat room's slide-out menu. (Events is a separate feature from LINE Calendar — it opens a server-hosted page.) |  |
| [Hide Home modules](#hide-home-modules) | Hides Home-tab clutter modules: the recommended stickers/content section, the real-time hot-topics (即時夯話題) block, and Home feed ads. |  |
| [Hide LINE GIFT button](#hide-line-gift-button) | Removes the LINE GIFT tile from a chat room's + attach menu. |  |
| [Hide LINE TODAY tab](#hide-line-today-tab) | Removes the LINE TODAY (News) tab from the main bottom navigation, in both the news-tab and news-row layouts. |  |
| [Hide Transfer button](#hide-transfer-button) | Removes the Transfer (LINE Pay) tile from a chat room's + attach menu. |  |
| [Hide VOOM tab](#hide-voom-tab) | Removes the VOOM (formerly Timeline) tab from the main bottom navigation. |  |
| [Hide Wallet tab](#hide-wallet-tab) | Removes the Wallet (LINE Pay) tab from the main bottom navigation, in both the normal and mini-tab layouts. |  |
| [Hide ad views](#hide-ad-views) | Hides LINE display ad views — the LINE Ads SDK containers across the app, the chat-list Smart Channel banner, and Google AdManager ads. |  |
| [Hide attach menu extra tools](#hide-attach-menu-extra-tools) | Removes all the server-provided extra tools from a chat room's + attach menu (Poll, Reservation, Schedule, Ladder shuffle, and any others). The built-in tiles (camera, gallery, files, contact, etc.) are unaffected. |  |
| [Hide calendar buttons](#hide-calendar-buttons) | Removes every LINE Calendar button inside the messenger: the one in the Chats-tab header, and the four inside a chat room — the top toolbar, the + attach menu, the slide-out chat menu, and the message long-press menu. |  |
| [Hide community button](#hide-community-button) | Removes the community (OpenChat) button from the top of the Chats tab header. |  |
| [Keep chats unread](#keep-chats-unread) | Opening a 1:1 or group chat no longer marks it read — it stays unread and no read receipt is sent. Manually using "Mark as read" / "Mark all as read" still marks the chat read and sends the receipt as normal. |  |
| [Open links in external browser](#open-links-in-external-browser) | Opens tapped web links (http/https) in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE deep links are unaffected. |  |
| [Remove banner ads](#remove-banner-ads) | Stops LINE from loading Smart Channel banner ads (neutralizes the getBanners and getPrefetchableBanners responses). |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

Or manually add this repository url as a patch source in Morphe: https://github.com/andrewliang25/morphe-patches

### 🛠️ Building

To build Andrew's Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 🙏 Special thanks

- [@f870103](https://github.com/f870103) — for lending a LINE account for testing.

## 📜 License

Andrew's Patches are licensed under the [GNU General Public License v3.0](LICENSE)
