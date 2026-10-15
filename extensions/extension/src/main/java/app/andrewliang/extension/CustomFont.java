package app.andrewliang.extension;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Helper for the "[General] Custom font" patch.
 *
 * <p>LINE draws its premium fonts through a downloadable font: the {@code premium_font} style sets
 * {@code @font/premium_font}, which asks LINE's own {@code PremiumFontProvider} for a font file.
 * This class serves a font file that the user picked from the device through the same path:
 *
 * <ol>
 *   <li>LINE puts the style on each Activity only while a premium font is in use, so
 *       {@link #onProviderCreate} does the same while a custom font is set.
 *   <li>androidx accepts a font provider only when the provider app is signed with a
 *       certificate in {@code @array/fonts_certs}. A re-signed LINE fails that check, so
 *       {@link #allowOwnProvider} accepts LINE's own provider.
 *   <li>{@link #query} and {@link #openFile} answer for the custom font.
 * </ol>
 *
 * <p>{@link #wrapSettingsView} adds the buttons below LINE's Font page, and
 * {@link CustomFontActivity} picks or removes the file and restarts LINE.
 *
 * <p>While the switch "Custom font" of "[General] Andrew's Patch Setting" is off, each method
 * here keeps LINE's own behavior. The font file stays, so the font comes back when the switch is
 * on again.
 */
public final class CustomFont {

    private CustomFont() {}

    private static final String PREFERENCES = "andrewliang_custom_font";
    private static final String KEY_NAME = "name";
    private static final String FILE_NAME = "andrewliang_custom_font";

    /** LINE's green, used for the buttons so that they read the same on each LINE theme. */
    private static final int GREEN = 0xFF06C755;

    /** Gray that can be read on a light and on a dark page. */
    private static final int GRAY = 0xFF8A8A8A;

    private static Context appContext;

    static File fontFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    static boolean hasFont(Context context) {
        return context != null && fontFile(context).isFile();
    }

    /** Whether to serve the custom font: the switch is on and a font file is set. */
    private static boolean useFont() {
        return LineSettings.customFont() && hasFont(appContext);
    }

    static String fontName(Context context) {
        return preferences(context).getString(KEY_NAME, null);
    }

    static void setFontName(Context context, String name) {
        SharedPreferences.Editor editor = preferences(context).edit();
        if (name == null) editor.remove(KEY_NAME);
        else editor.putString(KEY_NAME, name);
        editor.commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /**
     * Called from {@code PremiumFontProvider.onCreate}, which runs once when the process starts,
     * before {@code Application.onCreate}.
     */
    public static void onProviderCreate(ContentProvider provider) {
        Context context = provider.getContext();
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (!useFont()) return;

        int style = appContext.getResources().getIdentifier(
                "premium_font", "style", appContext.getPackageName());
        if (style == 0) return;
        // When a premium font is also in use, LINE puts the same style on each Activity again.
        // Applying a style twice has the effect of applying it once.
        ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                activity.setTheme(style);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    /**
     * Called where the androidx font provider check found no matching certificate, in place of
     * its {@code null}. Accepts a provider of this app, because a re-signed LINE never matches
     * LINE's certificates. A provider of another app still fails.
     */
    public static ProviderInfo allowOwnProvider(ProviderInfo provider) {
        if (provider == null || provider.packageName == null || !LineSettings.customFont()) return null;
        return provider.packageName.equals(ownPackage()) ? provider : null;
    }

    private static String ownPackage() {
        if (appContext != null) return appContext.getPackageName();
        // Only the main process creates the provider. Other processes find the package here.
        String process = Application.getProcessName();
        int colon = process.indexOf(':');
        return colon < 0 ? process : process.substring(0, colon);
    }

    /**
     * Called before {@code PremiumFontProvider.query} returns. With a custom font, gives one
     * regular-weight row, so that Android makes bold text from the custom font. Without one,
     * keeps LINE's answer.
     */
    public static Cursor query(Cursor original, String[] selectionArgs) {
        if (!useFont() || selectionArgs == null || selectionArgs.length == 0) return original;
        if (original != null) original.close();
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "font_weight"});
        cursor.addRow(new Object[]{selectionArgs[0], 400});
        return cursor;
    }

    /**
     * Called at the start of {@code PremiumFontProvider.openFile}. Returns the custom font, or
     * {@code null} to let LINE serve its own font.
     */
    public static ParcelFileDescriptor openFile() {
        if (!useFont()) return null;
        try {
            return ParcelFileDescriptor.open(fontFile(appContext), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Called before {@code FontSettingFragment.onCreateView} returns. LINE draws the page with
     * Jetpack Compose, so the buttons go below LINE's view and not into its list.
     */
    public static View wrapSettingsView(View page) {
        if (!LineSettings.customFont()) return page;
        Context context = page.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View bar = buttons(context);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The window under the bar does not have the color of LINE's page, and LINE's theme can
        // change that color. So the bar takes the color that the page draws at its bottom edge.
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private int frames;

            @Override
            public boolean onPreDraw() {
                Integer color = bottomColor(page);
                if (color != null) bar.setBackgroundColor(color);
                if (color != null || ++frames > 120) root.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
        return root;
    }

    /**
     * The color of one pixel at the bottom left of {@code page}, or null while the page draws
     * nothing there.
     */
    private static Integer bottomColor(View page) {
        int width = page.getWidth();
        int height = page.getHeight();
        if (width < 4 || height < 4) return null;
        Bitmap pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(pixel);
            canvas.translate(-2, -(height - 2));
            page.draw(canvas);
            int color = pixel.getPixel(0, 0);
            return Color.alpha(color) == 255 ? color : null;
        } catch (Exception e) {
            return null;
        } finally {
            pixel.recycle();
        }
    }

    private static View buttons(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        bar.setPadding(padding, dp(context, 8), padding, padding);

        boolean hasFont = hasFont(context);
        if (hasFont) {
            TextView current = new TextView(context);
            String name = fontName(context);
            current.setText("Custom font: " + (name != null ? name : "set"));
            current.setTextColor(GRAY);
            current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            current.setGravity(Gravity.CENTER);
            current.setPadding(0, 0, 0, dp(context, 8));
            bar.addView(current);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (hasFont) {
            Button remove = button(context, "Remove", false);
            remove.setOnClickListener(v -> start(context, CustomFontActivity.ACTION_REMOVE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 48), 1f);
            params.setMarginEnd(dp(context, 8));
            row.addView(remove, params);
        }
        Button upload = button(context, hasFont ? "Change font" : "Upload font from device", true);
        upload.setOnClickListener(v -> start(context, CustomFontActivity.ACTION_PICK));
        row.addView(upload, new LinearLayout.LayoutParams(0, dp(context, 48), 1f));
        bar.addView(row);
        return bar;
    }

    private static Button button(Context context, String text, boolean filled) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setStateListAnimator(null);
        button.setTextColor(filled ? Color.WHITE : GREEN);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 24));
        if (filled) shape.setColor(GREEN);
        else shape.setStroke(dp(context, 1), GREEN);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x33000000), shape, null));
        return button;
    }

    private static void start(Context context, String action) {
        context.startActivity(new Intent(context, CustomFontActivity.class).setAction(action));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
