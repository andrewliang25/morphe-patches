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
> **[v1.0.0-dev.7](https://github.com/andrewliang25/morphe-patches/releases/tag/v1.0.0-dev.7)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;10 patches total
<details open>
<summary>📦 LINE&nbsp;&nbsp;•&nbsp;&nbsp;10 patches</summary>
<br>

**🎯 Supported versions:**

| 26.11.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable LINE Pay](#disable-line-pay) | Closes any LINE Pay screen immediately on open, so Pay flows (and their device-integrity check) never run. Messaging is unaffected. |  |
| [Disable VOOM](#disable-voom) | Neutralizes VOOM entry points: VOOM deep links, shares, and notifications (line://home/*) do nothing, and the standalone VOOM feed closes on open. Also disables VOOM LIVE, the birthday board, and share-to-VOOM. Messaging, friend profiles, and other tabs are unaffected. |  |
| [Hide Home modules](#hide-home-modules) | Hides selected Home-tab modules (bottom ad, recommended content sections). EXPERIMENTAL — blocklist being tuned. |  |
| [Hide LINE TODAY tab](#hide-line-today-tab) | Removes the LINE TODAY (News) tab from the main bottom navigation, in both the news-tab and news-row layouts. |  |
| [Hide VOOM tab](#hide-voom-tab) | Removes the VOOM (formerly Timeline) tab from the main bottom navigation. |  |
| [Hide Wallet tab](#hide-wallet-tab) | Removes the Wallet (LINE Pay) tab from the main bottom navigation, in both the normal and mini-tab layouts. |  |
| [Hide ad views](#hide-ad-views) | Hides LINE display ad views — the LINE Ads SDK containers across the app, the chat-list Smart Channel banner, and Google AdManager ads. |  |
| [Open links in external browser](#open-links-in-external-browser) | Opens tapped web links (http/https) in your default browser instead of LINE's in-app browser. LIFF mini-apps and LINE deep links are unaffected. |  |
| [Prevent read receipts](#prevent-read-receipts) | Stops LINE from telling senders when you have read their messages, across 1:1, group, and OpenChat rooms. |  |
| [Remove banner ads](#remove-banner-ads) | Stops LINE from loading Smart Channel banner ads (neutralizes the getBanners and getPrefetchableBanners responses). |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=andrewliang25/morphe-patches

Or manually add this repository url as a patch source in Morphe: https://github.com/andrewliang25/morphe-patches

### 🛠️ Building

To build Andrew's Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

Andrew's Patches are licensed under the [GNU General Public License v3.0](LICENSE)
