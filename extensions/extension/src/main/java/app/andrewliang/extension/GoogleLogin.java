package app.andrewliang.extension;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper for the "[Fix] Restore Google login via MicroG-RE" patch.
 *
 * <p>"Continue with Google" asks androidx.credentials for a Google ID token. On Android 14 and
 * later, androidx.credentials hands the request to the system Credential Manager, and Play
 * Services checks LINE's real signing certificate there, in its own process. A re-signed build
 * fails that check. The patch sends only this request down the path that LINE's own copy of the
 * Play Services provider runs in LINE's process: its Identity Sign-In client. That client then
 * binds to MicroG-RE, which reports LINE's original certificate.
 *
 * <p>Every method returns LINE's original value when the request is not a Google ID token request,
 * when MicroG-RE is not installed, or on any error.
 */
public final class GoogleLogin {

    private GoogleLogin() {}

    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String MICROG_PACKAGE = "app.revanced.android.gms";
    private static final String GMS_SIGN_IN_ACTION =
            "com.google.android.gms.auth.api.identity.service.signin.START";
    /** MicroG-RE serves the same service under its own package prefix. */
    private static final String MICROG_SIGN_IN_ACTION =
            "app.revanced.android.gms.auth.api.identity.service.signin.START";
    /** The type of the "Sign in with Google" credential option. */
    private static final String GOOGLE_ID_TOKEN_TYPE =
            "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL";

    /** How long a routed request may take to reach the Sign-In client. */
    private static final long ARM_MILLIS = 60_000L;
    private static long armedUntil;

    /** The decision for each Sign-In client, made once when it reads its package. */
    private static final Map<Object, Boolean> ROUTED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Called first in the androidx.credentials provider choice. True makes LINE use its own Play
     * Services provider instead of the system Credential Manager.
     */
    public static boolean usePlayServicesProvider(Object request) {
        try {
            return isGoogleIdTokenRequest(request) && isMicrogInstalled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Called with the Play Services provider's "use the newer Identity Credentials API" check. For a
     * Google ID token request, returns false, so the provider uses the Sign-In client, and lets the
     * next Sign-In client connect to MicroG-RE.
     */
    public static boolean useIdentityCredentialsApi(boolean original, Object request) {
        try {
            if (!isGoogleIdTokenRequest(request) || !isMicrogInstalled()) return original;
            synchronized (GoogleLogin.class) {
                armedUntil = SystemClock.elapsedRealtime() + ARM_MILLIS;
            }
            return false;
        } catch (Throwable ignored) {
            return original;
        }
    }

    /**
     * The package the Sign-In client binds to. The connection reads this first, so the decision for
     * the client is made here and used up: one routed request moves one connection.
     */
    public static String signInPackage(Object client) {
        boolean routed;
        synchronized (GoogleLogin.class) {
            routed = SystemClock.elapsedRealtime() < armedUntil;
            armedUntil = 0L;
        }
        try {
            ROUTED.put(client, routed);
        } catch (Throwable ignored) {
            return GMS_PACKAGE;
        }
        return routed ? MICROG_PACKAGE : GMS_PACKAGE;
    }

    /** The service action the Sign-In client binds to. */
    public static String signInAction(Object client) {
        return isRouted(client) ? MICROG_SIGN_IN_ACTION : GMS_SIGN_IN_ACTION;
    }

    /**
     * Whether the Sign-In client asks Play Services to look up the service. That lookup ignores the
     * package and always returns Play Services, so a routed client must not use it.
     */
    public static boolean signInDynamicLookup(Object client) {
        return !isRouted(client);
    }

    private static boolean isRouted(Object client) {
        try {
            return Boolean.TRUE.equals(ROUTED.get(client));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * True when the request has at least one option and every option is a Google ID token option.
     * A request with a passkey or a password stays on LINE's original path. The request classes are
     * obfuscated, so this reads their fields: the request holds a list of options, and each option
     * holds its type as a string.
     */
    static boolean isGoogleIdTokenRequest(Object request) throws IllegalAccessException {
        if (request == null) return false;
        for (Field field : instanceFields(request.getClass())) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(request);
            if (!(value instanceof List)) continue;
            List<?> options = (List<?>) value;
            if (options.isEmpty()) continue;
            for (Object option : options) {
                if (!hasStringValue(option, GOOGLE_ID_TOKEN_TYPE)) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean hasStringValue(Object object, String expected) throws IllegalAccessException {
        if (object == null) return false;
        for (Field field : instanceFields(object.getClass())) {
            if (field.getType() != String.class) continue;
            field.setAccessible(true);
            if (expected.equals(field.get(object))) return true;
        }
        return false;
    }

    private static java.util.ArrayList<Field> instanceFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) fields.add(field);
            }
        }
        return fields;
    }

    private static boolean isMicrogInstalled() {
        Context context = currentApplication();
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(MICROG_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static Context currentApplication() {
        try {
            Object application = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
