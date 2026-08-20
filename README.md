# 🧩 Andrew's Patches

Morphe patches for LINE and other apps I use.

## ❓ About

A collection of [Morphe](https://github.com/MorpheApp) patches, currently focused on
[LINE](https://line.me) (`jp.naver.line.android`). Apply them with the Morphe CLI or
Morphe Manager to build a modified APK.

> 💡 **Don't want to build it yourself?** Grab ready-made patched apps from
> [andrewliang25/patched-apps](https://github.com/andrewliang25/patched-apps/releases).

> This is an independent project and is not affiliated with, endorsed by, or authored by
> the Morphe open source project, LINE, or LY Corporation.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.6.1-dev.1](https://github.com/andrewliang25/morphe-patches/releases/tag/v1.6.1-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;21 patches total
<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;21 patches</summary>
<br>

**🎯 Supported versions:**

| 26.11.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable LINE Premium](#disable-line-premium) | Hides all LINE Yahoo Premium (LYP) surfaces — upsells, badges, the Premium settings page, and subscribe/manage flows. Premium chat backup falls back to the ordinary chat-history backup. Doesn't unlock anything (premium is server-enforced). |  |
| [Disable VOOM](#disable-voom) | Neutralizes VOOM entry points — deep links, shares, and notifications do nothing and the standalone VOOM feed closes on open. Messaging and other tabs are unaffected. |  |
| [Fix chat backup sign-in via GmsCore](#fix-chat-backup-sign-in-via-gmscore) | Routes chat-history backup's Google account picker and Drive token through GmsCore, so backup and restore work on re-signed builds. Requires MicroG-RE. Doesn't affect Google account login. Root Mount install does not need this patch. |  |
| [Fix push notifications](#fix-push-notifications) | Restores push notifications on re-signed builds when LINE is fully closed. Root Mount install does not need this patch. |  |
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
| [Hide premium unsend upsells](#hide-premium-unsend-upsells) | Removes the LYP premium-unsend upsells that survive "Disable LINE Premium": the "Unsend discreetly" button, the post-unsend promo link, and the expired-window unsend upsell. Ordinary unsend still works. |  |
| [Keep chats unread](#keep-chats-unread) | Opening a 1:1 or group chat no longer marks it read — it stays unread and no read receipt is sent. Manually using "Mark as read" / "Mark all as read" still marks the chat read and sends the receipt as normal. |  |
| [Keep unsent messages](#keep-unsent-messages) | Keeps messages that were unsent in 1:1 and group chats on your device instead of destroying them, and shows the usual "unsent a message" notice directly below the message it kept. Doesn't apply to OpenChat. |  |
| [Open links in external browser](#open-links-in-external-browser) | Opens tapped web links (http/https) in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE deep links are unaffected. |  |
| [Redirect LINE Pay](#redirect-line-pay) | Forwards LINE Pay flows to the standalone LINE Pay app instead of running them in-app, so the device-integrity check that fails on a re-signed build never runs. Messaging is unaffected. |  |
| [Remove banner ads](#remove-banner-ads) | Stops LINE from loading Smart Channel banner ads (neutralizes the getBanners and getPrefetchableBanners responses). |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

Or manually add this repository url as a patch source in Morphe: https://github.com/andrewliang25/morphe-patches

### 🛠️ Building

To build Andrew's Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## ⚠️ Known limitations

### LINE: Google account login fails (re-signed builds)

**What:** On a patched **LINE** build, logging in with — or linking — a Google account fails.

**Why:** Google accepts an account only for an OAuth client registered under LINE's package name
**and** its original signing certificate, which re-signing changes. LINE asks Android's Credential
Manager for the account, so the *system* picks Google Play Services and no patch can intervene
([details](docs/line-patch-map.md)).

**Workaround:** install with **Root Mount**, which keeps LINE's original signature, instead of
**Standard** install.

Chat-history backup is *not* affected: the *Fix chat backup sign-in via GmsCore* patch restores it
via [MicroG-RE](https://github.com/MorpheApp/MicroG-RE).

## 🙏 Special thanks

- [@f870103](https://github.com/f870103) — for lending a LINE account for testing, and for finding the LINE Pay app redirect URL.

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
