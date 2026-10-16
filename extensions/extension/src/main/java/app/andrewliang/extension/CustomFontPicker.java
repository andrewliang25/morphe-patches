package app.andrewliang.extension;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.fonts.Font;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Picks or removes the custom font, then closes LINE.
 *
 * <p>It is a hidden framework Fragment on LINE's own Activity, so it needs no entry in the
 * manifest. A Root Mount install keeps the manifest of the original app, so an Activity of the
 * patch does not exist there.
 *
 * <p>A restart in a new task is not sufficient. LINE's Compose screens, for example the chat list,
 * keep the font of the process, and Android keeps a loaded font file in the process. So a change
 * shows on all screens only after LINE closes and the user opens it again.
 */
@SuppressWarnings("deprecation")
public final class CustomFontPicker extends Fragment {

    static final String ACTION_PICK = "pick";
    static final String ACTION_REMOVE = "remove";

    private static final String FRAGMENT_TAG = "andrewliang_custom_font_picker";
    private static final String ARG_ACTION = "action";
    private static final int REQUEST_PICK = 1;

    /** Mime types that file apps give to font files. Some give no font type at all. */
    private static final String[] MIME_TYPES = {
            "font/ttf", "font/otf", "font/collection", "font/sfnt",
            "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
            "application/vnd.ms-opentype", "application/octet-stream",
    };

    static void start(Activity activity, String action) {
        CustomFontPicker picker = new CustomFontPicker();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ACTION, action);
        picker.setArguments(arguments);
        activity.getFragmentManager().beginTransaction().add(picker, FRAGMENT_TAG).commitAllowingStateLoss();
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        Activity activity = getActivity();

        if (ACTION_REMOVE.equals(getArguments().getString(ARG_ACTION))) {
            //noinspection ResultOfMethodCallIgnored
            CustomFont.fontFile(activity).delete();
            CustomFont.setFontName(activity, null);
            askClose(activity);
            return;
        }

        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
        try {
            startActivityForResult(pick, REQUEST_PICK);
        } catch (Exception e) {
            Toast.makeText(activity, "No app can open a file.", Toast.LENGTH_LONG).show();
            close();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK) return;
        Activity activity = getActivity();
        Uri uri = data != null ? data.getData() : null;
        if (resultCode != Activity.RESULT_OK || uri == null) {
            close();
            return;
        }
        if (!save(activity, uri)) {
            Toast.makeText(activity, "LINE cannot use this file as a font.", Toast.LENGTH_LONG).show();
            close();
            return;
        }
        askClose(activity);
    }

    private void close() {
        FragmentManager manager = getFragmentManager();
        if (manager != null) manager.beginTransaction().remove(this).commitAllowingStateLoss();
    }

    /** Copies the file next to the font in use, checks that it is a font, then replaces it. */
    private static boolean save(Context context, Uri uri) {
        File target = CustomFont.fontFile(context);
        File copy = new File(target.getPath() + ".new");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(copy)) {
            if (in == null) return false;
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            copy.delete();
            return false;
        }

        try {
            // Throws when the file is not a font that Android can read.
            new Font.Builder(copy).build();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            copy.delete();
            return false;
        }

        if (!copy.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            copy.delete();
            return false;
        }
        CustomFont.setFontName(context, displayName(context, uri));
        return true;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
            // The file app gives no name.
        }
        return null;
    }

    /** A font change shows only after LINE opens again. See the class comment. */
    private void askClose(Activity activity) {
        dialog(activity)
                .setMessage("LINE closes to apply the font change. Open LINE again after it closes.")
                .setNegativeButton(lineString(activity, "line_fontsetting_button_cancel", "Cancel"), null)
                .setPositiveButton("Close LINE", (d, which) -> exit(activity))
                .setOnDismissListener(d -> close())
                .show();
    }

    private static AlertDialog.Builder dialog(Activity activity) {
        boolean night = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return new AlertDialog.Builder(activity, night
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    private static String lineString(Context context, String name, String fallback) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return id != 0 ? context.getString(id) : fallback;
    }

    /** Closes every task of LINE and ends the process. The user opens LINE again. */
    static void exit(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.AppTask task : manager.getAppTasks()) task.finishAndRemoveTask();
        }
        System.exit(0);
    }
}
