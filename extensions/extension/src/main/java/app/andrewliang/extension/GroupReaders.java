package app.andrewliang.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "[Chat] Show who read messages": a "Read by" cell in the long-press menu of a message in a group,
 * and a list of the members who read the message and the members who did not.
 *
 * <p>LINE keeps, for each member of a chat, the range of messages that the member read, in the
 * {@code read_message_range} table of its {@code read_notification} database. A member read message
 * M when a range has {@code from_msg_id < M <= to_msg_id}. LINE counts the same rows for its own
 * "Read N" label. The table has no row for this account, and sending a message does not count as
 * reading it.
 *
 * <p>Before the list shows, LINE's own read-range sync runs for the chat, so that the list has the
 * reads that LINE has not fetched yet.
 */
public final class GroupReaders {

    private GroupReaders() {}

    private static final String TAG = "AndrewReaders";

    // The patch replaces these two stubs with calls into LINE.

    /** The account id (mid) of this phone, or null. */
    static String ownMid(Context context) {
        return null;
    }

    /** Runs LINE's read-range sync for one chat. Returns LINE's error, or null. Blocks: not on the main thread. */
    static Object syncReadRange(Context context, String chatId) {
        return null;
    }

    /**
     * Called by the patch after LINE builds the menu grid and before it colors it. Puts "Read by"
     * in the first empty cell of the last row, or in a new row.
     */
    public static void addCell(View grid, Object menu, String chatId, long messageId, String sender) {
        try {
            if (!LineSettings.showReaders() || !(grid instanceof ViewGroup) || !isGroup(chatId) || messageId <= 0) {
                return;
            }
            Context context = grid.getContext();
            ViewGroup rows = (ViewGroup) grid;
            int rowContainerId = id(context, "chat_ui_message_context_menu_row_container");
            int titleId = id(context, "chat_ui_message_context_menu_item_title");
            int imageId = id(context, "chat_ui_message_context_menu_item_image");
            if (rows.getChildCount() == 0 || rowContainerId == 0 || titleId == 0 || imageId == 0) return;

            ViewGroup lastRow = rows.getChildAt(rows.getChildCount() - 1).findViewById(rowContainerId);
            if (lastRow == null || lastRow.getChildCount() == 0) return;
            View filled = lastRow.getChildAt(0);
            View cell = null;
            for (int i = 0; i < lastRow.getChildCount(); i++) {
                TextView title = lastRow.getChildAt(i).findViewById(titleId);
                if (title != null && title.getText().length() == 0) {
                    cell = lastRow.getChildAt(i);
                    break;
                }
            }
            if (cell == null) cell = newRow(context, rows, lastRow.getChildCount());
            if (cell == null) return;

            TextView title = cell.findViewById(titleId);
            ImageView image = cell.findViewById(imageId);
            String label = LineStrings.get(context, "Read by");
            title.setText(label);
            cell.setContentDescription(label);
            int icon = context.getResources().getIdentifier("andrew_ic_readers", "drawable", context.getPackageName());
            if (icon != 0) image.setImageResource(icon);
            image.setVisibility(View.VISIBLE);
            cell.setBackgroundTintList(filled.getBackgroundTintList());
            cell.setOnClickListener(v -> {
                if (menu instanceof DialogInterface) ((DialogInterface) menu).dismiss();
                show(context, chatId, messageId, sender);
            });
        } catch (Throwable t) {
            Log.e(TAG, "addCell", t);
        }
    }

    /** Adds a row of {@code columns} empty cells, as LINE does, and returns its first cell. */
    private static View newRow(Context context, ViewGroup rows, int columns) {
        int rowLayout = layout(context, "chat_ui_message_context_menu_content_row");
        int itemLayout = layout(context, "chat_ui_message_context_menu_content_item");
        int rowContainerId = id(context, "chat_ui_message_context_menu_row_container");
        if (rowLayout == 0 || itemLayout == 0) return null;
        LayoutInflater inflater = LayoutInflater.from(context);
        View row = inflater.inflate(rowLayout, rows, false);
        ViewGroup container = row.findViewById(rowContainerId);
        if (container == null) return null;
        for (int i = 0; i < columns; i++) container.addView(inflater.inflate(itemLayout, container, false));
        rows.addView(row);
        return container.getChildAt(0);
    }

    /** Group chat ids start with "c", and multi-person chat ids with "r". */
    private static boolean isGroup(String chatId) {
        return chatId != null && !chatId.isEmpty() && (chatId.charAt(0) == 'c' || chatId.charAt(0) == 'r');
    }

    private static void show(Context context, String chatId, long messageId, String sender) {
        Activity activity = activity(context);
        if (activity == null || activity.isFinishing()) return;
        AlertDialog dialog = new AlertDialog.Builder(activity, dialogTheme(activity))
                .setTitle(LineStrings.get(activity, "Read by"))
                .setMessage("…")
                .setPositiveButton(android.R.string.ok, null)
                .show();
        Context app = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String text = list(app, activity, chatId, messageId, sender);
            main.post(() -> {
                if (dialog.isShowing()) dialog.setMessage(text);
            });
        }, "AndrewReaders").start();
    }

    /**
     * A light or dark dialog, after LINE's own theme: LINE's {@code primaryBackground} color
     * follows its light, dark and system theme setting, not only the phone.
     */
    private static int dialogTheme(Context context) {
        int id = context.getResources().getIdentifier("primaryBackground", "color", context.getPackageName());
        boolean dark = id != 0 && Color.luminance(context.getColor(id)) < 0.5f;
        return dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
    }

    /** The text of the list. Runs on a worker thread. */
    private static String list(Context app, Context ui, String chatId, long messageId, String sender) {
        try {
            Object error = syncReadRange(app, chatId);
            if (error != null) Log.w(TAG, "sync: " + error);
        } catch (Throwable t) {
            Log.w(TAG, "sync", t);
        }
        String id = Long.toString(messageId);
        Set<String> read = new LinkedHashSet<>(strings(app, "read_notification",
                "SELECT DISTINCT member_id FROM read_message_range WHERE chat_id=? AND from_msg_id<? AND to_msg_id>=?",
                chatId, id, id));
        Set<String> members = new LinkedHashSet<>(strings(app, "naver_line", chatId.charAt(0) == 'c'
                        ? "SELECT m_id FROM membership WHERE id=? AND is_accepted=1"
                        : "SELECT mid FROM chat_member WHERE chat_id=?",
                chatId));
        String me = null;
        try {
            me = ownMid(app);
        } catch (Throwable t) {
            Log.w(TAG, "ownMid", t);
        }
        Set<String> notRead = new LinkedHashSet<>(members);
        notRead.removeAll(read);
        for (Set<String> set : List.of(read, notRead)) {
            set.remove(me);
            set.remove(sender);
        }
        return section(ui, "Read", names(app, read)) + "\n\n" + section(ui, "Not read", names(app, notRead));
    }

    private static String section(Context ui, String heading, List<String> names) {
        StringBuilder text = new StringBuilder(LineStrings.get(ui, heading)).append(" (").append(names.size()).append(')');
        for (String name : names) text.append('\n').append(name);
        return text.toString();
    }

    /** The names that LINE shows for {@code mids}: the name the user set, else the profile name. */
    private static List<String> names(Context app, Set<String> mids) {
        List<String> names = new ArrayList<>();
        if (mids.isEmpty()) return names;
        // A group has at most 500 members, below SQLite's limit of 999 arguments.
        String marks = String.join(",", Collections.nCopies(mids.size(), "?"));
        names.addAll(strings(app, "contact",
                "SELECT COALESCE(NULLIF(overridden_name, ''), profile_name) FROM contacts WHERE mid IN (" + marks + ")",
                mids.toArray(new String[0])));
        // A member with no contact row has no name on this phone.
        for (int i = names.size(); i < mids.size(); i++) names.add("?");
        Collections.sort(names, Collator.getInstance());
        return names;
    }

    /** The first column of {@code sql} in LINE's database {@code name}, read only. Empty on any error. */
    private static List<String> strings(Context app, String name, String sql, String... args) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(app.getDatabasePath(name).getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    String value = cursor.getString(0);
                    if (value != null) out.add(value);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "query " + name, t);
        } finally {
            if (db != null) db.close();
        }
        return out;
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    private static int layout(Context context, String name) {
        return context.getResources().getIdentifier(name, "layout", context.getPackageName());
    }
}
