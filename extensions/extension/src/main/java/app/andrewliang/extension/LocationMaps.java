package app.andrewliang.extension;

import android.content.Context;
import android.util.Log;

/**
 * Helper for the "Fix location maps via GmsCore" patch.
 *
 * <p>LINE draws every map through the Google Maps Android SDK v2 <i>thin client</i>. The renderer
 * itself is not in LINE's APK: LINE asks {@code DynamiteModule} for
 * {@code com.google.android.gms.maps_dynamite}, receives a remote {@link Context}, and class-loads
 * {@code com.google.android.gms.maps.internal.CreatorImpl} out of it. That renderer then binds
 * {@code com.google.android.gms.maps.auth.ApiTokenService} <b>in the Play Services process</b>,
 * which reads LINE's real signing certificate from {@code PackageManager} and asks Google's server
 * for a tile token. On a re-signed build the certificate no longer matches the restriction on
 * LINE's API key, the token is refused, and every map renders as an empty grid.
 *
 * <p>Nothing in LINE's own bytecode reports that certificate, so it cannot be corrected the way
 * "Fix push notifications" rewrites the {@code X-Android-Cert} header. Instead this returns
 * MicroG-RE's {@link Context}, whose bundled MapLibre/VTM renderer validates no key and no
 * signature. MicroG-RE added Maps support for exactly this redirect.
 *
 * <p>Returns {@code null} when MicroG-RE is not installed, which makes the patch a no-op: LINE
 * falls through to its original body and keeps talking to real Play Services. Only the maps module
 * context is redirected — {@code DynamiteModule} itself is untouched, so ads, ML Kit and TFLite
 * still load from Play Services.
 */
public final class LocationMaps {

    private LocationMaps() {}

    private static final String TAG = "AndrewLineMaps";

    /** MicroG-RE's applicationId. Its namespace stays {@code com.google.android.gms}. */
    private static final String MICROG_PACKAGE = "app.revanced.android.gms";

    /**
     * The class LINE loads by name out of the module context. MicroG-RE keeps this exact name on
     * purpose. It is also the test for whether a given MicroG-RE build has a Maps renderer at all.
     */
    private static final String CREATOR_CLASS = "com.google.android.gms.maps.internal.CreatorImpl";

    /**
     * The result of the one attempt, or {@code null} when there is no usable renderer.
     *
     * <p>LINE stores the module context in a static field and returns it on later calls. The patch
     * returns before that store, so without a cache here every map would rebuild the context.
     */
    private static Context cached;

    /** Set after the first attempt, so a miss costs no more than a hit. */
    private static boolean tried;

    /**
     * MicroG-RE's context, with its code loaded, or {@code null} when it cannot draw a map.
     *
     * @param context any LINE context; only used to reach the package manager.
     */
    public static Context getMapsContext(Context context) {
        if (tried) return cached;
        if (context == null) return null;
        tried = true;

        try {
            // CONTEXT_INCLUDE_CODE loads MicroG-RE's classes and native libraries into LINE's
            // process, which is where the renderer has to run. IGNORE_SECURITY is required
            // because the two apps are signed with different certificates.
            Context microG = context.createPackageContext(
                MICROG_PACKAGE,
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
            );

            // MicroG-RE only got a Maps renderer in 7.0.0. An older build has the package but not
            // the class, and the package alone is not enough: LINE's loader would take this
            // context, fail to load the class, return null, and then crash on the null renderer.
            // Load the class here instead, so an older build falls back to Play Services.
            microG.getClassLoader().loadClass(CREATOR_CLASS);

            cached = microG;
            Log.i(TAG, "Loading maps from MicroG-RE.");
        } catch (Throwable t) {
            // NameNotFoundException: MicroG-RE is not installed, or LINE cannot see it because the
            // manifest <queries> entry is missing. ClassNotFoundException: MicroG-RE is older than
            // 7.0.0. Either way, leave the map to Play Services rather than break it further.
            cached = null;
            Log.w(TAG, "MicroG-RE maps unavailable; leaving maps on Play Services.", t);
        }

        return cached;
    }
}
