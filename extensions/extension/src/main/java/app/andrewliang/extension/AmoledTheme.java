package app.andrewliang.extension;

import android.graphics.Color;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper for the "[General] AMOLED black theme" patch. It holds the rule for route one and route
 * four. See {@code docs/facebook-theme-map.md}.
 *
 * <p>The decision needs the token, because a colour alone cannot show the difference between a card
 * and a dark divider. The names of the tokens carry that difference, and R8 cannot rename an enum
 * constant.
 *
 * <p>The decision also needs the colour, because the same tokens serve light mode, where a card is
 * white. Thus the patch needs no test for dark mode.
 *
 * <p>{@link #apply} runs for each colour on each layout pass. Thus it makes no object, loads no
 * class and writes no log.
 */
public final class AmoledTheme {

    private AmoledTheme() {}

    /**
     * The largest value that a channel can have and still count as a background. Measured on a
     * device: a card is {@code #252728}, but a divider is {@code #3A3B3C}.
     */
    private static final int MAX_CHANNEL = 0x2A;

    /**
     * The largest difference between the channels of a background. A grey has almost none. A dark
     * green or dark brown banner has much more, and it keeps its colour.
     */
    private static final int MAX_SPREAD = 8;

    /**
     * The tokens that name a background area. The short names come from Mig and the long names
     * from FDS. One set holds both, because only the Mig surface enum declares the short names.
     */
    private static final Set<String> BACKGROUND_TOKENS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    // Mig.
                    "WASH",
                    "SURFACE",
                    "CARD",
                    "ELEVATION",
                    "BANNER",
                    "PRIMARY_UI",
                    // FDS: the page.
                    "WEB_WASH",
                    "FBLITE_WASH",
                    "SURFACE_BACKGROUND",
                    "BACKGROUND_SURFACE",
                    "DEVICE_BACKGROUND",
                    "BACKGROUND_DEEMPHASIZED",
                    // FDS: panels on the page.
                    "CARD_BACKGROUND",
                    "CARD_BACKGROUND_FLAT",
                    "CARD_BACKGROUND_LEGACY_WEB",
                    "BACKGROUND_CARD",
                    "BACKGROUND_ELEVATION",
                    "LIST_CELL_BACKGROUND",
                    "ATTACHMENT_FOOTER_BACKGROUND",
                    "ENTITY_HEADER_BACKGROUND",
                    // FDS: comments.
                    "COMMENT_BACKGROUND",
                    "COMMENT_BACKGROUND_DEEMPHASIZED",
                    // FDS: sheets and popovers.
                    "BOTTOM_SHEET_BACKGROUND_DEEMPHASIZED",
                    "BOTTOM_SHEET_INSET_BACKGROUND",
                    "POPOVER_BACKGROUND",
                    "FADED_POPOVER_BACKGROUND",
                    // FDS: chrome.
                    "NAV_BAR_BACKGROUND",
                    "TAB_BAR_BACKGROUND",
                    "BACKGROUND_BANNER",
                    "BACKGROUND_PRIMARY_UI")));

    /**
     * Route one: a colour that a resolver returns.
     *
     * @param token an enum constant. Only its name is used.
     * @return black if this is a background that is already dark, or {@code color} unchanged.
     */
    public static int apply(int color, Object token) {
        if (!isDarkNeutral(color)) return color;
        if (!(token instanceof Enum)) return color;

        return BACKGROUND_TOKENS.contains(((Enum<?>) token).name()) ? 0xFF000000 : color;
    }

    /**
     * Route four: a colour that the server sends as text.
     *
     * <p>The patch replaces each call to {@link Color#parseColor} in the app with a call to this
     * method. A server-driven screen, such as Settings, gets its colours as strings like
     * {@code "#FF252728"}. No token comes with a string, so the colour alone decides, as in route
     * two and route three.
     *
     * <p>A text that is not a colour throws the same exception as before, so the callers see no
     * change.
     */
    public static int parseColor(String text) {
        int color = Color.parseColor(text);
        return isDarkNeutral(color) ? 0xFF000000 : color;
    }

    /** True for an opaque grey with each channel at or below {@link #MAX_CHANNEL}. */
    private static boolean isDarkNeutral(int color) {
        if ((color >>> 24) != 0xFF) return false;

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int high = Math.max(red, Math.max(green, blue));
        int low = Math.min(red, Math.min(green, blue));
        return high <= MAX_CHANNEL && high - low <= MAX_SPREAD;
    }
}
