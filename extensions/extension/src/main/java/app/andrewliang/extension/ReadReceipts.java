package app.andrewliang.extension;

/**
 * Helper for the "Prevent read receipts" LINE patch.
 *
 * Injected at the top of the generic Thrift client dispatch
 * (org.apache.thrift.o.b(methodName, args)). Rewriting the outbound method name to a no-op
 * for the read-receipt ops neutralizes them across every Thrift service (1:1, group, and
 * OpenChat/Square) while keeping the send/recv pair balanced so the transport stays in sync.
 */
public final class ReadReceipts {

    private ReadReceipts() {}

    /**
     * @param methodName the Thrift op about to be sent.
     * @return {@code "noop"} for a read-receipt op (so the server ignores it and replies with
     *         a harmless, caught error), otherwise {@code methodName} unchanged.
     */
    public static String rewrite(String methodName) {
        if (methodName == null) {
            return null;
        }
        switch (methodName) {
            case "sendChatChecked":   // 1:1 & group chats (TalkService)
            case "markAsRead":        // OpenChat / Square rooms
            case "markChatsAsRead":
            case "markThreadsAsRead":
                return "noop";
            default:
                return methodName;
        }
    }
}
