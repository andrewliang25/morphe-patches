package app.andrewliang.extension;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Helper for the "Keep unsent messages" patch.
 *
 * <p>The patch makes LINE skip the database work it normally does when a message is unsent in a
 * 1:1 or group chat, so the row survives untouched — text, media, reactions and its search-index
 * entry all intact. Nothing then marks the message as recalled, so we add that marker ourselves:
 * a second, synthetic {@code chat_history} row of type {@code i38.c.UNSENT}.
 *
 * <p>That type is all LINE's renderer needs. It maps the row to the {@code i38.g.s.h0} content
 * model, which resolves to {@code chathistory_message_format_unsent_receiver}
 * ("%1$s unsent a message.") — or the "You unsent a message." variant when {@code from_mid} is
 * your own account. Copying {@code from_mid} from the original row therefore gives correct sender
 * naming and free localisation, with no injected strings.
 *
 * <p>Best-effort: any failure is swallowed, leaving the kept message in place without a notice.
 */
public final class KeepUnsentMessages {

    private KeepUnsentMessages() {}

    private static final String TAG = "AndrewKeepUnsent";

    /** {@code i38.c.UNSENT} db value — the "this message was unsent" chat_history.type. */
    private static final int TYPE_UNSENT = 27;

    /** {@code cb8.q7.NONE} db value, matching what LINE's own unsend writes. */
    private static final int ATTACHMENT_NONE = 0;

    /**
     * Suffix appended to the original {@code server_id} to form the placeholder's own id. Keeps
     * the synthetic row distinguishable from real server rows and makes the insert idempotent.
     */
    private static final String MARK = "_unsent";

    /**
     * Copies the identifying columns of the unsent message into a new tombstone row.
     *
     * <p>{@code created_time} is a TEXT column holding epoch millis, so the +1 that orders the
     * placeholder directly below its message needs a cast round-trip. Ordering follows
     * {@code created_time} (the {@code IDX_CHAT_ID_ID_CREATED_TIME} sort columns), so the notice
     * lands under the message rather than at the end of the conversation; a one millisecond
     * offset is invisible at the minute precision the UI displays.
     *
     * <p>{@code status}, {@code read_count} and {@code sent_count} are inherited so the
     * placeholder cannot skew unread counts. {@code content} is left NULL deliberately — for this
     * type the renderer ignores it and builds the text from {@code from_mid}.
     */
    private static final String INSERT_PLACEHOLDER =
            "INSERT INTO chat_history"
                    + " (server_id, type, chat_id, from_mid, created_time, delivered_time,"
                    + " status, read_count, sent_count, attachement_type)"
                    + " SELECT o.server_id || ?, ?, o.chat_id, o.from_mid,"
                    + " CAST(CAST(o.created_time AS INTEGER) + 1 AS TEXT), o.delivered_time,"
                    + " o.status, o.read_count, o.sent_count, ?"
                    + " FROM chat_history o"
                    + " WHERE (o.server_id = ? OR o.id = ?)"
                    + " AND o.server_id IS NOT NULL"
                    + " AND o.created_time IS NOT NULL"
                    + " AND NOT EXISTS ("
                    + "  SELECT 1 FROM chat_history x WHERE x.server_id = o.server_id || ?)"
                    + " LIMIT 1";

    /**
     * Best-effort: add the "unsent a message" notice under the message we just kept.
     *
     * @param db        the open chat database, inside LINE's own unsend transaction.
     * @param messageId the unsent message's id. The caller reads whichever of the row's two long
     *                  ids is already loaded at the patch site, so this may be either the local
     *                  {@code chat_history.id} or the {@code server_id}; the statement matches on
     *                  either. Local ids are small and server ids are large, so the two id spaces
     *                  do not realistically overlap, and {@code LIMIT 1} bounds it regardless.
     */
    public static void insertPlaceholder(SQLiteDatabase db, long messageId) {
        if (db == null) return;
        try {
            db.execSQL(INSERT_PLACEHOLDER, new Object[] {
                    MARK,
                    Integer.valueOf(TYPE_UNSENT),
                    Integer.valueOf(ATTACHMENT_NONE),
                    String.valueOf(messageId),
                    Long.valueOf(messageId),
                    MARK,
            });
        } catch (Throwable t) {
            Log.w(TAG, "Could not add the unsent notice; keeping the message without one.", t);
        }
    }
}
