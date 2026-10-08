package app.andrewliang.extension;

import android.content.Context;
import android.util.Log;

import java.io.Serializable;

/**
 * The tap handler of the "Andrew's Patch Setting" row in LINE's Settings list.
 *
 * <p>LINE gives each row two handlers, both obfuscated Kotlin function types. The settings patch
 * adds both interfaces to this class at patch time, so its methods here must keep these names and
 * shapes:
 * <ul>
 *   <li>{@link #invoke(Object)} is the one-argument function. The main Settings list calls it with
 *       the list fragment when the row is tapped.</li>
 *   <li>{@link #j(Object, Object, Object)} is the three-argument function, (Context, List, settings
 *       navigation fragment). Settings search calls it.</li>
 * </ul>
 * LINE writes the three-argument handler into a Parcel as a {@link Serializable}, so this class must
 * stay serializable.
 */
public final class LineSettingsClick implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String TAG = "AndrewLineSettings";

    public Object invoke(Object fragment) {
        try {
            // LINE renames the androidx Fragment class but keeps its method names.
            Object context = fragment.getClass().getMethod("getContext").invoke(fragment);
            LineSettingsScreen.show((Context) context);
        } catch (Throwable t) {
            Log.w(TAG, "Could not open the settings screen", t);
        }
        return null;
    }

    public Object j(Object context, Object rows, Object navigation) {
        try {
            LineSettingsScreen.show((Context) context);
        } catch (Throwable t) {
            Log.w(TAG, "Could not open the settings screen", t);
        }
        return null;
    }
}
