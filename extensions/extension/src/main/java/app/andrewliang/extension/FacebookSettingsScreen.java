package app.andrewliang.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The "Andrew's Patch Setting" screen for Facebook: the runtime switches of
 * {@link FacebookSettings}, then credits and the license. It also builds the row that opens it.
 *
 * <p>It is a full-screen {@link Dialog} over Facebook's main activity, so it needs no manifest
 * entry and no fragment. Facebook removes almost all resource names, so every view is built in
 * code with Facebook's light and dark colours. The background comes from the Menu drawer under
 * the dialog, so Facebook's dark mode and the AMOLED theme carry over.
 */
final class FacebookSettingsScreen {

    private FacebookSettingsScreen() {}

    static final int MENU_ROW_HEIGHT_DP = 52;

    private static final String SOURCE_URL = "https://github.com/andrewliang25/morphe-patches";
    private static final String LICENSE_URL = SOURCE_URL + "/blob/main/LICENSE";

    /** Facebook's blue, used for the switches. */
    private static final int ACCENT = 0xFF0866FF;

    /** The colours of one screen: Facebook's light or dark set on the drawer's background. */
    private static final class Palette {
        final int background;
        final int text;
        final int secondary;
        final int divider;
        final int press;
        final boolean light;

        Palette(Context context, int drawerBackground) {
            boolean light = drawerBackground != 0
                    ? luminance(drawerBackground) > 0.5
                    : (context.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            this.light = light;
            background = drawerBackground != 0 ? drawerBackground : light ? Color.WHITE : 0xFF242526;
            text = light ? 0xFF050505 : 0xFFE4E6EB;
            secondary = light ? 0xFF65676B : 0xFFB0B3B8;
            divider = light ? 0xFFCED0D4 : 0xFF3E4042;
            press = light ? 0x1A000000 : 0x1AFFFFFF;
        }

        private static double luminance(int color) {
            return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        }
    }

    /** The row at the top of the Menu drawer: a gear and the title, with a line below. */
    static View menuRow(Context context, int drawerBackground) {
        Palette palette = new Palette(context, drawerBackground);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(context, 12);
        row.setPadding(horizontal, 0, horizontal, 0);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.press),
                new ColorDrawable(palette.background), null));
        row.setContentDescription("Andrew's Patch Setting");

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(new GearDrawable(palette.text, 1.6f * context.getResources().getDisplayMetrics().density));
        int size = dp(context, 24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        iconParams.setMarginEnd(dp(context, 12));
        row.addView(icon, iconParams);

        TextView title = text(context, "Andrew's Patch Setting", 17, palette.text);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // The line is drawn by a wrapper, so the ripple stays above it.
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(palette.background);
        wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View line = new View(context);
        line.setBackgroundColor(palette.divider);
        wrapper.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        row.setOnClickListener(v -> {
            ViewGroup parent = (ViewGroup) wrapper.getParent();
            int current = parent == null ? 0 : FacebookSettings.backgroundOf(parent);
            show(context, current != 0 ? current : drawerBackground);
        });
        return wrapper;
    }

    static void show(Context context, int drawerBackground) {
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing()) return;
        Palette palette = new Palette(activity, drawerBackground);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.addView(header(activity, dialog, palette));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addSwitches(activity, content, palette);
        addCredits(activity, content, palette);

        // The dialog draws behind the system bars, so the screen keeps clear of them.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(palette.background));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            int light = palette.light
                    ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    : 0;
            WindowInsetsController bars = window.getInsetsController();
            if (bars != null) {
                bars.setSystemBarsAppearance(light, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
        dialog.show();
    }

    private static void addSwitches(Activity activity, LinearLayout content, Palette palette) {
        boolean feed = FacebookSettings.hideSponsoredPostsIncluded() || FacebookSettings.hidePageSuggestionsIncluded()
                || FacebookSettings.hidePostPromptsIncluded() || FacebookSettings.blockFeedAutoRefreshIncluded();
        boolean reels = FacebookSettings.hideSponsoredReelsIncluded() || FacebookSettings.hideReelPromptsIncluded()
                || FacebookSettings.downloadReelIncluded() || FacebookSettings.reelsPipIncluded();
        boolean stories = FacebookSettings.hideSponsoredStoriesIncluded() || FacebookSettings.downloadStoryIncluded()
                || FacebookSettings.anonymousStoryViewsIncluded()
                || FacebookSettings.disableStoryAutoAdvanceIncluded();
        boolean general = FacebookSettings.hideAffiliateLinksIncluded() || FacebookSettings.externalBrowserIncluded()
                || FacebookSettings.amoledThemeIncluded();

        if (!feed && !reels && !stories && !general) {
            content.addView(note(activity, "No patches in this build have a switch.", palette));
            return;
        }

        View restart = row(activity, "Restart Facebook", "Shows your changes now.", palette);
        restart.setVisibility(View.GONE);
        restart.setOnClickListener(v -> restart(activity));
        Runnable showRestart = () -> restart.setVisibility(View.VISIBLE);

        if (feed) {
            content.addView(section(activity, "Feed", palette));
            if (FacebookSettings.hideSponsoredPostsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_SPONSORED_POSTS, "Hide sponsored posts",
                        null, palette, showRestart));
            }
            if (FacebookSettings.hidePageSuggestionsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_PAGE_SUGGESTIONS,
                        "Hide page suggestions and promo cards", "\"Suggested for you\" posts stay.",
                        palette, showRestart));
            }
            if (FacebookSettings.hidePostPromptsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_POST_PROMPTS, "Hide post prompts",
                        "Such as \"Are you interested in this post?\".", palette, showRestart));
            }
            if (FacebookSettings.blockFeedAutoRefreshIncluded()) {
                content.addView(toggle(activity, FacebookSettings.BLOCK_FEED_AUTO_REFRESH, "Block feed auto refresh",
                        "Keeps your place in the feed when you come back.", palette, showRestart));
            }
        }

        if (reels) {
            content.addView(section(activity, "Reels", palette));
            if (FacebookSettings.hideSponsoredReelsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_SPONSORED_REELS, "Hide sponsored reels",
                        null, palette, showRestart));
            }
            if (FacebookSettings.hideReelPromptsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_REEL_PROMPTS, "Hide interest prompts",
                        "\"Are you interested in this reel?\"", palette, showRestart));
            }
            if (FacebookSettings.downloadReelIncluded()) {
                content.addView(toggle(activity, FacebookSettings.DOWNLOAD_REEL, "Download any reel",
                        "The download button on the side of a reel.", palette, showRestart));
            }
            if (FacebookSettings.reelsPipIncluded()) {
                content.addView(toggle(activity, FacebookSettings.REELS_PIP, "Picture-in-picture",
                        "A reel keeps playing in a small window when you leave Facebook.", palette, showRestart));
            }
        }

        if (stories) {
            content.addView(section(activity, "Stories", palette));
            if (FacebookSettings.hideSponsoredStoriesIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_SPONSORED_STORIES, "Hide sponsored stories",
                        null, palette, showRestart));
            }
            if (FacebookSettings.downloadStoryIncluded()) {
                content.addView(toggle(activity, FacebookSettings.DOWNLOAD_STORY, "Download any story",
                        "\"Save\" in the story menu.", palette, showRestart));
            }
            if (FacebookSettings.anonymousStoryViewsIncluded()) {
                content.addView(toggle(activity, FacebookSettings.ANONYMOUS_STORY_VIEWS, "View stories anonymously",
                        "Others do not see that you viewed their story.", palette, showRestart));
            }
            if (FacebookSettings.disableStoryAutoAdvanceIncluded()) {
                content.addView(toggle(activity, FacebookSettings.DISABLE_STORY_AUTO_ADVANCE, "Disable auto advance",
                        "A story stays until you tap or swipe.", palette, showRestart));
            }
        }

        if (general) {
            content.addView(section(activity, "General", palette));
            if (FacebookSettings.hideAffiliateLinksIncluded()) {
                content.addView(toggle(activity, FacebookSettings.HIDE_AFFILIATE_LINKS, "Hide affiliate product links",
                        "Shop link cards in Reels, posts and comments.", palette, showRestart));
            }
            if (FacebookSettings.externalBrowserIncluded()) {
                content.addView(toggle(activity, FacebookSettings.EXTERNAL_BROWSER, "Open links in external browser",
                        "Opens web links in your default browser.", palette, showRestart));
            }
            if (FacebookSettings.amoledThemeIncluded()) {
                // Facebook keeps some colours for the life of the process, so a restart in a new
                // task does not change the status bar and the top bar.
                View close = row(activity, "Close Facebook", "Open Facebook again to apply the theme.", palette);
                close.setVisibility(View.GONE);
                close.setOnClickListener(v -> CustomFontPicker.exit(activity));
                content.addView(toggle(activity, FacebookSettings.AMOLED_THEME, "AMOLED black theme",
                        "Black instead of dark grey in dark mode.", palette,
                        () -> close.setVisibility(View.VISIBLE)));
                content.addView(close);
            }
        }

        content.addView(note(activity, "Some changes show only after Facebook restarts.", palette));
        content.addView(restart);
    }

    private static void addCredits(Activity activity, LinearLayout content, Palette palette) {
        content.addView(section(activity, "Credits & license", palette));

        View about = row(activity, "Andrew's Patches", "By Andrew Liang", palette);
        about.setOnClickListener(v -> open(activity, SOURCE_URL));
        content.addView(about);

        View license = row(activity, "License", "GNU General Public License v3.0", palette);
        license.setOnClickListener(v -> showLicense(activity, palette));
        content.addView(license);

        content.addView(note(activity, "This project has no connection to the Morphe open source project, "
                + "Facebook, or Meta. They do not endorse it and did not write it.", palette));
    }

    /** A back arrow and the title, like the header of Facebook's own settings pages. */
    private static View header(Activity activity, Dialog dialog, Palette palette) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int height = dp(activity, 56);

        TextView back = text(activity, "←", 24, palette.text);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.press), null,
                new ColorDrawable(Color.WHITE)));
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(height, height));

        TextView title = text(activity, "Andrew's Patch Setting", 20, palette.text);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title);
        return header;
    }

    /** A switch row. A tap anywhere on the row flips the switch. */
    private static View toggle(Activity activity, String key, String title, String summary, Palette palette,
                               Runnable onChange) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        rowPadding(activity, row, palette);

        row.addView(texts(activity, title, summary, palette),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(activity);
        toggle.setChecked(FacebookSettings.isOn(key));
        toggle.setClickable(false);
        toggle.setFocusable(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, palette.light ? 0xFFFFFFFF : 0xFFB0B3B8}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[]{(ACCENT & 0x00FFFFFF) | 0x80000000, palette.light ? 0x40000000 : 0x60FFFFFF}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(activity, 12));
        row.addView(toggle, params);

        row.setOnClickListener(v -> {
            boolean on = !toggle.isChecked();
            toggle.setChecked(on);
            FacebookSettings.set(key, on);
            if (onChange != null) onChange.run();
        });
        return row;
    }

    /** A text row that opens something. */
    private static View row(Activity activity, String title, String summary, Palette palette) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        rowPadding(activity, row, palette);
        row.addView(texts(activity, title, summary, palette),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(activity, "›", 22, palette.secondary));
        return row;
    }

    private static void rowPadding(Activity activity, View row, Palette palette) {
        int horizontal = dp(activity, 16);
        int vertical = dp(activity, 12);
        row.setPadding(horizontal, vertical, horizontal, vertical);
        row.setMinimumHeight(dp(activity, 56));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.press), null,
                new ColorDrawable(Color.WHITE)));
    }

    private static View texts(Activity activity, String title, String summary, Palette palette) {
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(activity, title, 17, palette.text));
        if (summary != null) {
            TextView description = text(activity, summary, 14, palette.secondary);
            description.setPadding(0, dp(activity, 2), 0, 0);
            texts.addView(description);
        }
        return texts;
    }

    /** A section title, like the bold headers of Facebook's settings pages. */
    private static TextView section(Activity activity, String title, Palette palette) {
        TextView section = text(activity, title, 17, palette.text);
        section.setTypeface(section.getTypeface(), android.graphics.Typeface.BOLD);
        int horizontal = dp(activity, 16);
        section.setPadding(horizontal, dp(activity, 20), horizontal, dp(activity, 4));
        return section;
    }

    private static TextView note(Activity activity, String text, Palette palette) {
        TextView note = text(activity, text, 14, palette.secondary);
        int horizontal = dp(activity, 16);
        note.setPadding(horizontal, dp(activity, 8), horizontal, dp(activity, 8));
        return note;
    }

    private static TextView text(Context context, String text, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private static void showLicense(Activity activity, Palette palette) {
        TextView text = text(activity, LineSettingsScreen.LICENSE_NOTICE, 14, palette.text);
        text.setEllipsize(null);
        text.setTextIsSelectable(true);
        Linkify.addLinks(text, Linkify.WEB_URLS);
        int padding = dp(activity, 20);
        text.setPadding(padding, padding, padding, padding);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);

        int theme = palette.light ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert;
        new AlertDialog.Builder(activity, theme)
                .setTitle("License")
                .setView(scroll)
                .setNegativeButton("Full license", (d, w) -> open(activity, LICENSE_URL))
                .setPositiveButton("Close", null)
                .show();
    }

    private static void open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
            // No app can open the link.
        }
    }

    /**
     * Starts Facebook's launcher Activity as a new task, which creates each screen again. Do not
     * end the process here. Android starts the new task in the old process, and the exit removes it.
     */
    private static void restart(Activity activity) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (launch == null || launch.getComponent() == null) return;
        activity.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * An outline gear like Facebook's own row icons. Facebook keeps no gear drawable by name, so
     * the gear is drawn here: a ring with eight teeth, and a hole in the middle.
     */
    private static final class GearDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path gear = new Path();

        GearDrawable(int color, float stroke) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            // Drawn in a 24-unit box like a vector icon, then scaled to the bounds.
            float scale = bounds.width() / 24f;
            Path body = new Path();
            body.addCircle(12, 12, 6.8f, Path.Direction.CW);
            for (int i = 0; i < 8; i++) {
                Path tooth = new Path();
                tooth.addRoundRect(10.4f, 2.2f, 13.6f, 6.5f, 0.8f, 0.8f, Path.Direction.CW);
                android.graphics.Matrix rotate = new android.graphics.Matrix();
                rotate.setRotate(i * 45, 12, 12);
                tooth.transform(rotate);
                body.op(tooth, Path.Op.UNION);
            }
            body.addCircle(12, 12, 3, Path.Direction.CW);
            android.graphics.Matrix fit = new android.graphics.Matrix();
            fit.setScale(scale, scale);
            fit.postTranslate(bounds.left, bounds.top);
            gear.reset();
            body.transform(fit, gear);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(gear, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
