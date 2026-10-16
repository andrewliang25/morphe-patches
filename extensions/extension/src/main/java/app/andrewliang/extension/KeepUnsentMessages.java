package app.andrewliang.extension;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper for the "[Chat] Keep unsent messages" patch.
 *
 * <p>The patch makes LINE skip the database work it normally does when a message is unsent in a 1:1
 * or group chat, so the row survives untouched: text, media, reactions and search-index entry. This
 * class then marks the row, and shows an "Unsent" label in red in the place of LINE's "Edited" label,
 * next to the time of the message.
 *
 * <p>The mark is a key in the row's {@code parameter} column. That column holds the message's
 * content metadata as tab-separated {@code key\tvalue} pairs, and LINE's own "Edited" label is read
 * from the same column ({@code EDITED\ttrue}). LINE reads the column into a map for each message,
 * so the label costs no query. LINE's edit code reads the map and writes all of it back, so the key
 * stays when a kept message is edited.
 *
 * <p>The label goes from the database to the screen in three steps, all called from patched LINE
 * code:
 * <ol>
 *   <li>{@link #noteMetadata} reads the key where LINE reads {@code EDITED}.</li>
 *   <li>{@link #attach} copies it into a field that the patch adds to LINE's message view data. The
 *       patched {@code equals} of that class compares the field ({@link #sameMark}), so LINE draws
 *       the row again when a message is unsent while its chat is open.</li>
 *   <li>{@link #noteBound} and {@link #bindLabel} set the label when LINE binds the row.</li>
 * </ol>
 *
 * <p>Best-effort: any failure is caught, and the message then shows no label.
 */
public final class KeepUnsentMessages {

    private KeepUnsentMessages() {}

    private static final String TAG = "AndrewKeepUnsent";

    /** The metadata key that marks a kept unsent message. */
    private static final String KEY = "ANDREW_UNSENT";

    /** The {@code key\tvalue} pair that the mark adds to the {@code parameter} column. */
    private static final String PAIR = KEY + "\ttrue";

    /** The field that the patch adds to LINE's message view data class. */
    private static final String FIELD = "andrewUnsent";

    /**
     * The tombstone types, i.e. exactly what {@code na8.c.g()}, the guard the patch skips, returns
     * true for: {@code UNSENT}(27), {@code UNSENT_NO_MARK}(28), {@code UNSENT_SILENT}(38). LINE has
     * already stripped a row of one of these types and draws its own notice for it.
     */
    private static final String TYPES_ALREADY_UNSENT = "27, 28, 38";

    /**
     * Adds the mark to the row that has this id. The row must not be a tombstone, and must not have
     * the mark already.
     *
     * <p>An empty {@code parameter} gets the pair alone. A leading tab would shift every key and value
     * that LINE splits from the column by one.
     */
    private static final String MARK =
            "UPDATE chat_history SET parameter ="
                    + " CASE WHEN parameter IS NULL OR parameter = '' THEN ?"
                    + " ELSE parameter || char(9) || ? END"
                    + " WHERE id = ("
                    + "  SELECT id FROM chat_history"
                    + "  WHERE (server_id = ? OR id = ?)"
                    + "  AND server_id IS NOT NULL"
                    + "  AND type NOT IN (" + TYPES_ALREADY_UNSENT + ")"
                    + "  LIMIT 1)"
                    + " AND instr(char(9) || ifnull(parameter, '') || char(9),"
                    + " char(9) || ? || char(9)) = 0";

    /**
     * Earlier versions of the patch added a notice row, "X unsent a message.", below each kept
     * message: type {@code UNSENT}(27), with {@code server_id} set to the kept message's
     * {@code server_id} plus {@code "_unsent"}. These two statements mark each message that has
     * such a row, and then delete the rows.
     */
    private static final String OLD_NOTICES =
            "FROM chat_history WHERE type = 27 AND server_id LIKE '%!_unsent' ESCAPE '!'";

    private static final String MIGRATE_MARK =
            "UPDATE chat_history SET parameter ="
                    + " CASE WHEN parameter IS NULL OR parameter = '' THEN ?"
                    + " ELSE parameter || char(9) || ? END"
                    + " WHERE server_id IN ("
                    + "  SELECT substr(server_id, 1, length(server_id) - 7) " + OLD_NOTICES + ")"
                    + " AND type NOT IN (" + TYPES_ALREADY_UNSENT + ")"
                    + " AND instr(char(9) || ifnull(parameter, '') || char(9),"
                    + " char(9) || ? || char(9)) = 0";

    private static final String MIGRATE_DELETE = "DELETE " + OLD_NOTICES;

    private static volatile boolean migrated;

    /**
     * Best-effort: marks the message that was just kept.
     *
     * @param db        the open chat database, inside LINE's own unsend transaction.
     * @param messageId the unsent message's id: whichever of the row's two long ids is already
     *                  loaded at the patch site, so either the local {@code chat_history.id} or the
     *                  {@code server_id}. The statement matches on either. Local ids are small and
     *                  server ids large, so the two do not overlap in practice, and
     *                  {@code LIMIT 1} limits the update to one row regardless.
     */
    public static void markUnsent(SQLiteDatabase db, long messageId) {
        if (db == null) return;
        migrateOldNotices(db);
        try {
            db.execSQL(MARK, new Object[] {
                    PAIR, PAIR, String.valueOf(messageId), Long.valueOf(messageId), KEY,
            });
        } catch (Throwable t) {
            Log.w(TAG, "Could not mark the unsent message; it shows no label.", t);
        }
    }

    /**
     * Replaces the notice rows of earlier versions with the mark, once for each process. It runs
     * at the first unsend, inside LINE's transaction.
     */
    private static void migrateOldNotices(SQLiteDatabase db) {
        if (migrated) return;
        migrated = true;
        try {
            db.execSQL(MIGRATE_MARK, new Object[] {PAIR, PAIR, KEY});
            db.execSQL(MIGRATE_DELETE);
        } catch (Throwable t) {
            Log.w(TAG, "Could not replace the old unsent notices.", t);
        }
    }

    // ---- From the database to the view data ----

    /** The mark that {@link #noteMetadata} read, until {@link #attach} takes it. */
    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();

    /**
     * Called at the start of LINE's view data builder. It clears a mark that a failed build left,
     * so that it cannot go to the next message.
     */
    public static void clearPending() {
        PENDING.remove();
    }

    /**
     * Called where the builder reads {@code EDITED} from the message's metadata object.
     *
     * @param metadata LINE's metadata object, a wrapper around a {@code Map<String, String>}.
     */
    public static void noteMetadata(Object metadata) {
        try {
            Map<?, ?> map = mapOf(metadata);
            PENDING.set(map != null && "true".equals(map.get(KEY)));
        } catch (Throwable t) {
            PENDING.remove();
        }
    }

    /** Called with the view data object that the builder just made. */
    public static void attach(Object viewData) {
        Boolean unsent = PENDING.get();
        PENDING.remove();
        if (viewData == null || unsent == null || !unsent) return;
        try {
            markField(viewData.getClass()).setBoolean(viewData, true);
        } catch (Throwable t) {
            Log.w(TAG, "Could not mark the view data.", t);
        }
    }

    /**
     * Called at the start of the view data's {@code equals}. A false result makes {@code equals}
     * return false. Other results let LINE's own comparison decide.
     */
    public static boolean sameMark(Object a, Object b) {
        if (a == null || b == null || a.getClass() != b.getClass()) return true;
        return isMarked(a) == isMarked(b);
    }

    private static boolean isMarked(Object viewData) {
        try {
            return markField(viewData.getClass()).getBoolean(viewData);
        } catch (Throwable t) {
            return false;
        }
    }

    private static volatile Field markField;

    private static Field markField(Class<?> type) throws NoSuchFieldException {
        Field field = markField;
        if (field == null || field.getDeclaringClass() != type) {
            field = type.getDeclaredField(FIELD);
            field.setAccessible(true);
            markField = field;
        }
        return field;
    }

    private static volatile Field metadataMapField;

    /** The map inside LINE's metadata object: its only field of a {@link Map} type. */
    private static Map<?, ?> mapOf(Object metadata) throws IllegalAccessException {
        if (metadata == null) return null;
        Field field = metadataMapField;
        if (field == null || field.getDeclaringClass() != metadata.getClass()) {
            field = null;
            for (Field candidate : metadata.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(candidate.getType())
                        && !java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                    field = candidate;
                    break;
                }
            }
            if (field == null) return null;
            field.setAccessible(true);
            metadataMapField = field;
        }
        return (Map<?, ?>) field.get(metadata);
    }

    // ---- From the view data to the label ----

    /** The view data of the row that LINE binds now. Main thread only. */
    private static Object boundViewData;

    /** Called in LINE's row binder with the view data of the row. */
    public static void noteBound(Object viewData) {
        boundViewData = viewData;
    }

    /** LINE's text and color of each label that shows "Unsent" now. Main thread only. */
    private static final Map<TextView, Saved> SAVED = new WeakHashMap<>();

    private static final class Saved {
        final CharSequence text;
        final ColorStateList colors;

        Saved(TextView label) {
            text = label.getText();
            colors = label.getTextColors();
        }
    }

    /**
     * Called in LINE's row binder, after LINE has set the visibility of the "Edited" label.
     *
     * <p>LINE reuses a row's views for other messages, so a label that shows "Unsent" gets its own
     * text and color back when its row shows a message that was not unsent.
     *
     * @param controller LINE's controller of the "Edited" label. Its only {@link TextView} field is
     *                   the label.
     */
    public static void bindLabel(Object controller) {
        Object viewData = boundViewData;
        boundViewData = null;
        try {
            TextView label = labelOf(controller);
            if (label == null) return;
            if (viewData != null && isMarked(viewData)) {
                if (!SAVED.containsKey(label)) SAVED.put(label, new Saved(label));
                label.setText(text(label.getResources()));
                ColorStateList red = red(label.getResources(), label.getContext().getPackageName());
                if (red != null) label.setTextColor(red);
                label.setVisibility(View.VISIBLE);
            } else {
                Saved saved = SAVED.remove(label);
                if (saved != null) {
                    label.setText(saved.text);
                    label.setTextColor(saved.colors);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not set the unsent label.", t);
        }
    }

    private static volatile Field labelField;

    private static TextView labelOf(Object controller) throws IllegalAccessException {
        if (controller == null) return null;
        Field field = labelField;
        if (field == null || field.getDeclaringClass() != controller.getClass()) {
            field = null;
            for (Field candidate : controller.getClass().getDeclaredFields()) {
                if (candidate.getType() == TextView.class) {
                    field = candidate;
                    break;
                }
            }
            if (field == null) return null;
            field.setAccessible(true);
            labelField = field;
        }
        return (TextView) field.get(controller);
    }

    private static ColorStateList red;

    /** LINE's own red, {@code linered600}, resolved by name. */
    private static ColorStateList red(Resources resources, String packageName) {
        if (red == null) {
            int id = resources.getIdentifier("linered600", "color", packageName);
            if (id != 0) red = ColorStateList.valueOf(resources.getColor(id, null));
        }
        return red;
    }

    /**
     * The label in the language of LINE's resources. Each word is the past form of the word LINE
     * itself uses for "Unsend" in that language. Hong Kong Chinese and every other language get
     * English, as LINE's own "Edited" label does for Hong Kong.
     */
    static String text(Resources resources) {
        Locale locale = resources.getConfiguration().getLocales().get(0);
        String language = locale.getLanguage();
        String region = locale.getCountry();
        String script = locale.getScript();
        switch (language) {
            case "zh":
                if ("TW".equals(region)) return "已收回";
                if ("CN".equals(region) || "SG".equals(region)
                        || (region.isEmpty() && "Hans".equals(script))) return "已撤回";
                return "Unsent";
            case "ja": return "送信取消済み";
            case "ko": return "보내기 취소됨";
            case "th": return "ยกเลิกข้อความแล้ว";
            case "in":
            case "id": return "Diurungkan";
            case "es": return "Anulado";
            case "pt": return "Retirada";
            case "fr": return "Annulé";
            case "de": return "Zurückgezogen";
            case "vi": return "Đã hủy gửi";
            case "ru": return "Отозвано";
            case "it": return "Annullato";
            case "tr": return "Geri alındı";
            case "ar": return "أُلغي الإرسال";
            case "ms": return "Dinyahhantar";
            default: return "Unsent";
        }
    }
}
