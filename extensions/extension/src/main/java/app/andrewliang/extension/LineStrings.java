package app.andrewliang.extension;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Japanese and Traditional Chinese text of {@link LineSettingsScreen}, keyed by the English
 * text. The words follow LINE's own ja and zh-rTW strings.
 *
 * <p>The language is the locale of the Activity. LINE applies its own Language setting to that
 * locale, so the screen follows LINE and not only the phone. Any other language, and any text
 * with no entry here, stays English.
 */
final class LineStrings {

    private LineStrings() {}

    private static final int JA = 0;
    private static final int ZH_TW = 1;

    private static final Map<String, String[]> TEXT = new HashMap<>();

    private static void put(String english, String japanese, String chinese) {
        TEXT.put(english, new String[]{japanese, chinese});
    }

    static String get(Context context, String english) {
        String[] text = TEXT.get(english);
        if (text == null) return english;
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String language = locale.getLanguage();
        if (language.equals("ja")) return text[JA];
        if (language.equals("zh") && isTraditional(locale)) return text[ZH_TW];
        return english;
    }

    private static boolean isTraditional(Locale locale) {
        if (locale.getScript().equals("Hant")) return true;
        String country = locale.getCountry();
        return country.equals("TW") || country.equals("HK") || country.equals("MO");
    }

    static {
        put("No patches in this build have a switch.",
                "このビルドには、スイッチのあるパッチがありません。",
                "這個版本中沒有可切換的修補程式。");

        put("Chats", "トーク", "聊天");
        put("Keep chats unread", "トークを未読のままにする", "保持聊天未讀");
        put("Opening a chat does not mark it read or send a read receipt.",
                "トークを開いても既読にならず、既読も相手に送られません。",
                "開啟聊天時不會標為已讀，也不會傳送已讀通知。");
        put("Keep unsent messages", "送信取消されたメッセージを残す", "保留已收回的訊息");
        put("Keeps messages that others unsend, with a red \"Unsent\" label next to the time.",
                "相手が送信取消したメッセージを残し、時刻の横に赤い「Unsent」ラベルを表示します。",
                "保留對方收回的訊息，並在時間旁顯示紅色的「Unsent」標籤。");

        put("Chat buttons", "トークのボタン", "聊天按鈕");
        put("Restart LINE", "LINE を再起動", "重新啟動 LINE");
        put("Shows your button changes now.", "ボタンの変更をすぐに反映します。", "立即套用按鈕的變更。");
        put("Hide AI Friends button", "AI Friends ボタンを非表示", "隱藏 AI Friends 按鈕");
        put("In the Chats tab. The Album button shows in its place.",
                "トークタブにあります。代わりにアルバムボタンが表示されます。",
                "位於聊天分頁。原位置會改為顯示相簿按鈕。");
        put("Hide community button", "オープンチャットボタンを非表示", "隱藏社群按鈕");
        put("In the Chats tab.", "トークタブにあります。", "位於聊天分頁。");
        put("Hide calendar buttons", "カレンダーボタンを非表示", "隱藏行事曆按鈕");
        put("In the Chats tab, in chats and in the Home tab.",
                "トークタブ、トークルーム、ホームタブにあります。",
                "位於聊天分頁、聊天室和主頁分頁。");
        put("Hide Events button", "イベントボタンを非表示", "隱藏活動按鈕");
        put("In the chat menu.", "トークルームのメニューにあります。", "位於聊天室選單。");
        put("Hide LINE GIFT button", "LINE ギフトボタンを非表示", "隱藏 LINE GIFT 按鈕");
        put("In the + menu of a chat.", "トークルームの＋メニューにあります。", "位於聊天室的＋選單。");
        put("Hide Transfer button", "送金ボタンを非表示", "隱藏手機轉帳按鈕");
        put("Hide attach menu extra tools", "＋メニューの追加ツールを非表示", "隱藏＋選單的額外工具");
        put("Poll, Schedule and other server tools in the + menu of a chat.",
                "トークルームの＋メニューにある投票、日程調整など、サーバーから届くツールです。",
                "聊天室＋選單中的投票、Scheduling 等由伺服器提供的工具。");
        put("Hide tips under messages", "メッセージの下のヒントを非表示", "隱藏訊息下方的提示");
        put("The \"CHECK\" tips under some messages.",
                "一部のメッセージの下に出る「CHECK」のヒントです。",
                "部分訊息下方的「CHECK」提示。");
        put("Hide LINE AI image editing", "LINE AI の画像編集を非表示", "隱藏 LINE AI 圖片編輯");
        put("\"AI Edit\" in chats and the AI button in photo viewers.",
                "トークルームの「AI 編集」と、写真ビューアーの AI ボタンです。",
                "聊天室中的「AI 編輯」，以及照片檢視器中的 AI 按鈕。");
        put("Hide Agent i", "Agent i を非表示", "隱藏 Agent i");
        put("In Home, search, chats, the + menu, Wallet and Settings.",
                "ホーム、検索、トーク、＋メニュー、ウォレット、設定にあります。",
                "位於主頁、搜尋、聊天、＋選單、錢包和設定。");
        put("Some button changes show only after LINE restarts.",
                "一部のボタンの変更は、LINE の再起動後に反映されます。",
                "部分按鈕的變更會在 LINE 重新啟動後生效。");

        put("General", "一般", "一般");
        put("Open links in external browser", "リンクを外部ブラウザで開く", "用外部瀏覽器開啟連結");
        put("Opens web links in your default browser instead of LINE's browser.",
                "ウェブのリンクを、LINE のブラウザではなく端末の既定のブラウザで開きます。",
                "用裝置的預設瀏覽器開啟網頁連結，而不是 LINE 的瀏覽器。");
        put("Disable VOOM", "VOOM を無効にする", "停用 VOOM");
        put("VOOM links, shares and notifications do nothing.",
                "VOOM のリンク、シェア、通知をタップしても何も起きません。",
                "VOOM 的連結、分享和通知不會有任何動作。");
        put("Close LINE", "LINE を終了", "關閉 LINE");
        put("Open LINE again to apply your font change.",
                "LINE をもう一度開くと、フォントの変更が反映されます。",
                "重新開啟 LINE 後，字型的變更才會生效。");
        put("Custom font", "カスタムフォント", "自訂字型");
        put("Uses the font that you upload on LINE's Font page.",
                "LINE のフォント画面でアップロードしたフォントを使います。",
                "使用你在 LINE 字型頁面上傳的字型。");
        put("Open LINE again to apply your analytics change.",
                "LINE をもう一度開くと、分析の変更が反映されます。",
                "重新開啟 LINE 後，分析的變更才會生效。");
        put("Block analytics", "分析をブロック", "封鎖分析");
        put("Stops LINE from sending usage events, such as the screens you open and the buttons you tap.",
                "開いた画面やタップしたボタンなどの利用データを、LINE が送信しないようにします。",
                "阻止 LINE 傳送使用資料，例如你開啟的畫面和點擊的按鈕。");

        put("Calls", "通話", "通話");
        put("Auto-record calls", "通話を自動で録音・録画", "自動錄製通話");
        put("Starts recording a 1-to-1 call as soon as it connects.",
                "1 対 1 の通話がつながると、すぐに録音・録画を始めます。",
                "一對一通話接通後，立即開始錄製。");
        put("Record without notifying", "相手に知らせずに録音・録画", "錄製時不通知對方");
        put("The other person sees no banner and hears no announcement.",
                "相手にはバナーが表示されず、アナウンスも流れません。",
                "對方不會看到橫幅，也不會聽到提示音。");
        put("Save recordings to device", "録音・録画を端末に保存", "將錄製檔儲存到裝置");
        put("Saves recordings to Movies/LINE instead of Keep Memo, which refuses files over 1 GB.",
                "Keep メモではなく Movies/LINE に保存します。Keep メモは 1 GB を超えるファイルを受け付けません。",
                "儲存到 Movies/LINE，而不是 Keep 筆記。Keep 筆記不接受超過 1 GB 的檔案。");
        put("Remove recording time limit", "録音・録画の時間制限をなくす", "移除錄製時間限制");
        put("Records until the call ends. LINE normally stops after 20 to 60 minutes.",
                "通話が終わるまで録音・録画します。通常、LINE は 20〜60 分で停止します。",
                "錄製到通話結束為止。LINE 通常會在 20 到 60 分鐘後停止。");

        put("Tabs", "タブ", "分頁");
        put("Shows your tab changes now.", "タブの変更をすぐに反映します。", "立即套用分頁的變更。");
        put("Hide LINE TODAY tab", "LINE TODAY タブを非表示", "隱藏 LINE TODAY 分頁");
        put("Hide Shopping tab", "ショッピングタブを非表示", "隱藏購物分頁");
        put("Hide VOOM tab", "VOOM タブを非表示", "隱藏 VOOM 分頁");
        put("Hide Wallet tab", "ウォレットタブを非表示", "隱藏錢包分頁");
        put("Tab changes show after LINE restarts.",
                "タブの変更は、LINE の再起動後に反映されます。",
                "分頁的變更會在 LINE 重新啟動後生效。");

        put("Premium", "LINE Premium", "LINE Premium");
        put("Applies your Premium change.", "Premium の変更を反映します。", "套用 Premium 的變更。");
        put("Disable LINE Premium", "LINE Premium を無効にする", "停用 LINE Premium");
        put("Hides LINE Premium upsells, badges and pages.",
                "LINE Premium の勧誘、バッジ、画面を非表示にします。",
                "隱藏 LINE Premium 的推銷、徽章和頁面。");
        put("Unlock app icons", "アプリアイコンを解放", "解鎖應用程式圖示");
        put("Lets you use every app icon, including Premium and old collaboration icons.",
                "Premium や過去のコラボアイコンを含め、すべてのアプリアイコンを使えます。",
                "可使用所有應用程式圖示，包括 Premium 和過去的聯名圖示。");
        put("Premium changes show after LINE restarts.",
                "Premium の変更は、LINE の再起動後に反映されます。",
                "Premium 的變更會在 LINE 重新啟動後生效。");

        put("Credits & license", "クレジットとライセンス", "致謝與授權條款");
        put("By Andrew Liang", "作者：Andrew Liang", "作者：Andrew Liang");
        put("License", "ライセンス", "授權條款");
        put("This project has no connection to the Morphe open source project, LINE, or LY Corporation. "
                        + "They do not endorse it and did not write it.",
                "このプロジェクトは、Morphe オープンソースプロジェクト、LINE、LY Corporation とは関係ありません。"
                        + "これらの団体はこのプロジェクトを推奨しておらず、作成にも関わっていません。",
                "本專案與 Morphe 開源專案、LINE 及 LY Corporation 皆無關係。上述各方並未認可本專案，也未參與撰寫。");
        put("Full license", "ライセンス全文", "完整授權條款");
        put("Close", "閉じる", "關閉");
        put("Back", "戻る", "返回");
    }
}
