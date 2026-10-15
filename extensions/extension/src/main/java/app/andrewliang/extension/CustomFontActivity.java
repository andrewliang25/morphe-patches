package app.andrewliang.extension;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.fonts.Font;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The see-through Activity behind the buttons on LINE's Font page. It picks a font file or
 * removes the custom font, then offers to restart LINE.
 *
 * <p>Android keeps each loaded font in the process, so a new font shows only after LINE's process
 * starts again. The patch declares this Activity in its own process for {@link #restart}.
 */
public final class CustomFontActivity extends Activity {

    static final String ACTION_PICK = "app.andrewliang.extension.CUSTOM_FONT_PICK";
    static final String ACTION_REMOVE = "app.andrewliang.extension.CUSTOM_FONT_REMOVE";
    static final String ACTION_RESTART = "app.andrewliang.extension.CUSTOM_FONT_RESTART";

    private static final int REQUEST_PICK = 1;

    /** Mime types that file apps give to font files. Some give no font type at all. */
    private static final String[] MIME_TYPES = {
            "font/ttf", "font/otf", "font/collection", "font/sfnt",
            "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
            "application/vnd.ms-opentype", "application/octet-stream",
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;

        if (ACTION_RESTART.equals(getIntent().getAction())) {
            restart();
            return;
        }

        if (ACTION_REMOVE.equals(getIntent().getAction())) {
            //noinspection ResultOfMethodCallIgnored
            CustomFont.fontFile(this).delete();
            CustomFont.setFontName(this, null);
            askRestart();
            return;
        }

        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
        try {
            startActivityForResult(pick, REQUEST_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No app can open a file.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK) return;
        Uri uri = data != null ? data.getData() : null;
        if (resultCode != RESULT_OK || uri == null) {
            finish();
            return;
        }
        if (!save(uri)) {
            Toast.makeText(this, "LINE cannot use this file as a font.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        askRestart();
    }

    /** Copies the file next to the font in use, checks that it is a font, then replaces it. */
    private boolean save(Uri uri) {
        File target = CustomFont.fontFile(this);
        File copy = new File(target.getPath() + ".new");
        try (InputStream in = getContentResolver().openInputStream(uri);
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
        CustomFont.setFontName(this, displayName(uri));
        return true;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
            // The file app gives no name.
        }
        return null;
    }

    /** Asks with LINE's own words, so that the dialog is in the language of LINE. */
    private void askRestart() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        new AlertDialog.Builder(this, night
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setMessage(lineString("line_fontsetting_popuptitle_restarttochange",
                        "The app will restart when you apply a new font. Apply now?"))
                .setNegativeButton(lineString("line_fontsetting_button_cancel", "Cancel"), null)
                .setPositiveButton(lineString("line_fontsetting_button_restart", "Apply"),
                        (dialog, which) -> restart())
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    private String lineString(String name, String fallback) {
        int id = getResources().getIdentifier(name, "string", getPackageName());
        return id != 0 ? getString(id) : fallback;
    }

    /**
     * This Activity runs in its own process, so it can end LINE's main process first and then
     * start LINE in a new one. While this Activity is on the screen, Android lets it start LINE.
     */
    private void restart() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) {
            finish();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        ActivityManager manager = getSystemService(ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (process.processName.equals(getPackageName())) Process.killProcess(process.pid);
            }
        }
        startActivity(launch);
        finish();
        Process.killProcess(Process.myPid());
    }
}
