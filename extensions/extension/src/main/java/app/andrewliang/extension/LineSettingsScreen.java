package app.andrewliang.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "Andrew's Patch Setting" screen: the runtime switches of {@link LineSettings}, then credits
 * and the license.
 *
 * <p>It is a full-screen {@link Dialog} over LINE's Settings activity, so it needs no manifest
 * entry and no fragment. It looks like LINE's own settings pages because it inflates LINE's own
 * row layouts and icons. It finds them by resource name, which R8 keeps, because the ids change
 * between LINE versions.
 *
 * <p>The colors come from the Settings page under the dialog. LINE's theme engine paints a theme
 * that the user picks, such as Black, over the resource colors, so resources alone give the
 * wrong colors. {@link Look} copies what LINE drew, and the resources are only the fallback.
 */
final class LineSettingsScreen {

    private LineSettingsScreen() {}

    private static final String SOURCE_URL = "https://github.com/andrewliang25/morphe-patches";
    private static final String LICENSE_URL = SOURCE_URL + "/blob/main/LICENSE";

    private static final String LICENSE_NOTICE =
            "Andrew's Patches is free software: you can redistribute it and/or modify it under the "
                    + "terms of the GNU General Public License as published by the Free Software "
                    + "Foundation, either version 3 of the License, or (at your option) any later "
                    + "version.\n\n"
                    + "It is distributed in the hope that it will be useful, but WITHOUT ANY "
                    + "WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR "
                    + "A PARTICULAR PURPOSE. See the GNU General Public License for more details: "
                    + "https://www.gnu.org/licenses/gpl-3.0.html\n\n"
                    + "Morphe NOTICE\n\n"
                    + "7c. Project Name Restriction\n\n"
                    + "This license does not grant permission to use the name \"Morphe\", or any "
                    + "name, logo, or branding that is confusingly similar, as the name or primary "
                    + "identifying mark of any modified version, fork, or derivative work.\n\n"
                    + "Any redistribution or derivative work must adopt a distinct name and branding "
                    + "that clearly differentiates it from the Morphe project and does not cause "
                    + "confusion as to source or origin.\n\n"
                    + "References to Morphe are permitted solely for descriptive compatibility "
                    + "purposes (e.g., \"compatible with Morphe\" or \"XYZ patches for use with "
                    + "Morphe\"), provided that such references:\n\n"
                    + "- Are accurate, factual, and non-misleading;\n"
                    + "- Do not suggest authorship by the Morphe project; and\n"
                    + "- Appear only in a secondary or descriptive context, not as part of the "
                    + "product or project name.";

    static void show(Context context) {
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        look = Look.of(activity);
        int fallback = activity.getColor(id(activity, "primaryBackground", "color"));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(look.background(activity, fallback));
        root.addView(header(activity, dialog));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addSwitches(activity, content);
        addCredits(activity, content);

        // The dialog draws behind the system bars, so the screen keeps clear of them.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(look.background(activity, fallback));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            // Dark system bar icons on a light background, as on LINE's own pages.
            int light = look.isLight(fallback)
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

    private static void addSwitches(Activity activity, LinearLayout content) {
        boolean chats = LineSettings.keepChatsUnreadIncluded() || LineSettings.keepUnsentMessagesIncluded();
        boolean general = LineSettings.externalBrowserIncluded() || LineSettings.disableVoomIncluded();
        boolean tabs = LineSettings.hideTodayTabIncluded() || LineSettings.hideShoppingTabIncluded()
                || LineSettings.hideVoomTabIncluded() || LineSettings.hideWalletTabIncluded();

        if (!chats && !general && !tabs) {
            content.addView(note(activity, "No patches in this build have a switch."));
            return;
        }

        if (chats) {
            content.addView(section(activity, "Chats"));
            if (LineSettings.keepChatsUnreadIncluded()) {
                content.addView(toggle(activity, LineSettings.KEEP_CHATS_UNREAD, "Keep chats unread",
                        "Opening a chat does not mark it read or send a read receipt.", null));
            }
            if (LineSettings.keepUnsentMessagesIncluded()) {
                content.addView(toggle(activity, LineSettings.KEEP_UNSENT_MESSAGES, "Keep unsent messages",
                        "Keeps messages that others unsend, with a notice below each one.", null));
            }
        }

        if (general) {
            content.addView(section(activity, "General"));
            if (LineSettings.externalBrowserIncluded()) {
                content.addView(toggle(activity, LineSettings.EXTERNAL_BROWSER, "Open links in external browser",
                        "Opens web links in your default browser instead of LINE's browser.", null));
            }
            if (LineSettings.disableVoomIncluded()) {
                content.addView(toggle(activity, LineSettings.DISABLE_VOOM, "Disable VOOM",
                        "VOOM links, shares and notifications do nothing.", null));
            }
        }

        if (tabs) {
            content.addView(section(activity, "Tabs"));
            View restart = row(activity, "Restart LINE", "Shows your tab changes now.", true);
            restart.setVisibility(View.GONE);
            restart.setOnClickListener(v -> restart(activity));
            Runnable showRestart = () -> restart.setVisibility(View.VISIBLE);

            if (LineSettings.hideTodayTabIncluded()) {
                content.addView(toggle(activity, LineSettings.HIDE_TODAY_TAB, "Hide LINE TODAY tab", null, showRestart));
            }
            if (LineSettings.hideShoppingTabIncluded()) {
                content.addView(toggle(activity, LineSettings.HIDE_SHOPPING_TAB, "Hide Shopping tab", null, showRestart));
            }
            if (LineSettings.hideVoomTabIncluded()) {
                content.addView(toggle(activity, LineSettings.HIDE_VOOM_TAB, "Hide VOOM tab", null, showRestart));
            }
            if (LineSettings.hideWalletTabIncluded()) {
                content.addView(toggle(activity, LineSettings.HIDE_WALLET_TAB, "Hide Wallet tab", null, showRestart));
            }
            content.addView(note(activity, "Tab changes show after LINE restarts."));
            content.addView(restart);
        }
    }

    private static void addCredits(Activity activity, LinearLayout content) {
        content.addView(section(activity, "Credits & license"));

        View about = row(activity, "Andrew's Patches", "By Andrew Liang", true);
        about.setOnClickListener(v -> open(activity, SOURCE_URL));
        content.addView(about);

        View license = row(activity, "License", "GNU General Public License v3.0", true);
        license.setOnClickListener(v -> showLicense(activity));
        content.addView(license);

        content.addView(note(activity, "This project has no connection to the Morphe open source project, "
                + "LINE, or LY Corporation. They do not endorse it and did not write it."));
    }

    /** A back arrow and the title, like the header of LINE's own settings pages. */
    private static View header(Activity activity, Dialog dialog) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int height = activity.getResources().getDimensionPixelSize(id(activity, "header_height", "dimen"));
        header.setMinimumHeight(height);

        ImageView back = new ImageView(activity);
        back.setImageResource(id(activity, "header_ic_back_semantic", "drawable"));
        // LINE's theme engine colors the arrow on its view, not in the drawable, so the arrow
        // takes the color of the header title, as on LINE's own pages.
        ColorStateList titleColors = look.headerTitle != null ? look.headerTitle : look.title;
        if (titleColors != null) back.setImageTintList(titleColors);
        back.setContentDescription("Back");
        back.setPadding(dp(activity, 4), 0, 0, 0);
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));

        TextView title = new TextView(activity);
        title.setTextAppearance(id(activity, "text_header_title_new_design", "style"));
        title.setText("Andrew's Patch Setting");
        look.paint(title, titleColors);
        header.addView(title);
        return header;
    }

    /** LINE's switch row. A tap anywhere on the row flips the switch. */
    private static View toggle(Activity activity, String key, String title, String summary, Runnable onChange) {
        View row = inflate(activity, "line_user_settings_switch_item_view");
        text(activity, row, "setting_title", title);
        if (summary != null) text(activity, row, "setting_description", summary);
        // LINE's own switch pages draw no line between rows.
        row.findViewById(id(activity, "setting_divider", "id")).setVisibility(View.GONE);
        View container = row.findViewById(id(activity, "setting_item_container", "id"));
        look.press(container);
        CheckedTextView toggle = row.findViewById(id(activity, "setting_switch", "id"));
        toggle.setChecked(LineSettings.isOn(key));
        container.setOnClickListener(v -> {
            boolean on = !toggle.isChecked();
            toggle.setChecked(on);
            LineSettings.set(key, on);
            if (onChange != null) onChange.run();
        });
        return row;
    }

    /** LINE's text row, with the arrow when the row opens something. */
    private static View row(Activity activity, String title, String summary, boolean arrow) {
        View row = inflate(activity, "line_user_settings_text_item_view");
        text(activity, row, "setting_title", title);
        text(activity, row, "setting_description", summary);
        // The arrow is placed after the inline value, so that empty view keeps the arrow at the end.
        ((TextView) row.findViewById(id(activity, "setting_inlined_value", "id"))).setText("");
        row.findViewById(id(activity, "setting_arrow", "id")).setVisibility(arrow ? View.VISIBLE : View.GONE);
        // The caller listens on the whole row, so the container shows the row's pressed state.
        View container = row.findViewById(id(activity, "setting_item_container", "id"));
        container.setDuplicateParentStateEnabled(true);
        look.press(container);
        return row;
    }

    /** A section title, like LINE's group header row. */
    private static TextView section(Activity activity, String title) {
        TextView section = new TextView(activity);
        section.setTextAppearance(id(activity, "line_user_settings_group_header_item_text_semantic", "style"));
        int horizontal = dimen(activity, "line_user_settings_item_container_padding_horizontal");
        section.setPaddingRelative(horizontal, dimen(activity, "line_user_settings_item_header_small_padding_top"),
                horizontal, dimen(activity, "line_user_settings_item_header_padding_bottom"));
        section.setText(title);
        look.paint(section, look.sectionOrSecondary());
        return section;
    }

    /** LINE's description row. */
    private static View note(Activity activity, String text) {
        View note = inflate(activity, "line_user_settings_description_item");
        text(activity, note, "setting_description", text);
        return note;
    }

    private static View inflate(Activity activity, String layout) {
        return LayoutInflater.from(activity).inflate(id(activity, layout, "layout"), null, false);
    }

    private static void text(Activity activity, View row, String view, String text) {
        TextView textView = row.findViewById(id(activity, view, "id"));
        textView.setText(text);
        textView.setVisibility(View.VISIBLE);
        look.paint(textView, view.equals("setting_title") ? look.title : look.secondary());
    }

    /** The look of the screen, set by {@link #show}. Only one screen is open at a time. */
    private static Look look = new Look();

    /**
     * Colors copied from the LINE Settings page under the dialog. A null
     * field means LINE's page had no such view, and the resource color stays.
     */
    private static final class Look {
        Drawable background;
        ColorStateList title;
        ColorStateList description;
        ColorStateList section;
        ColorStateList headerTitle;

        static Look of(Activity activity) {
            Look look = new Look();
            try {
                // The page is the main Settings list, or the settings search results when the
                // row is opened from search. Each value tries the view of the list, then the view
                // of the search page.
                View page = activity.getWindow().getDecorView();
                look.background = copy(activity, background(activity, page));
                look.title = colors(find(activity, page, "setting_title", "item_title", "empty_title_text"));
                look.description = colors(find(activity, page, "setting_description", "item_path",
                        "empty_description_text"));
                look.section = colors(findByClass(page, "LineUserSettingGroupHeaderItemView"));
                View header = find(page, idOrZero(activity, "header", "id"));
                look.headerTitle = header != null
                        ? colors(findByClass(header, "TextView"))
                        : colors(find(activity, page, "input_text"));
            } catch (Throwable ignored) {
                // Keep what was found. The resources cover the rest.
            }
            return look;
        }

        Drawable background(Activity activity, int fallback) {
            Drawable copy = copy(activity, background);
            return copy != null ? copy : new ColorDrawable(fallback);
        }

        /** Whether the background is light, judged from the text color drawn on it. */
        boolean isLight(int fallback) {
            if (title != null) return Color.luminance(title.getDefaultColor()) < 0.5f;
            return Color.luminance(fallback) > 0.5f;
        }

        /** Descriptions, or section titles when LINE's page shows no description. */
        ColorStateList secondary() {
            return description != null ? description : section;
        }

        /** Section titles, or descriptions when LINE's page shows no section title. */
        ColorStateList sectionOrSecondary() {
            return section != null ? section : description;
        }

        /**
         * Gives [row] a press effect in the text color. LINE's row background has a light pressed
         * state that its theme engine repaints on LINE's own rows, so on these rows it flashes
         * white under a dark theme.
         */
        void press(View row) {
            if (title == null) return;
            int color = (title.getDefaultColor() & 0x00FFFFFF) | 0x1F000000;
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(color), null, new ColorDrawable(Color.WHITE)));
        }

        void paint(TextView view, ColorStateList colors) {
            if (colors != null) view.setTextColor(colors);
        }

        private static ColorStateList colors(View view) {
            return view instanceof TextView ? ((TextView) view).getTextColors() : null;
        }

        private static Drawable copy(Activity activity, Drawable drawable) {
            Drawable.ConstantState state = drawable == null ? null : drawable.getConstantState();
            return state == null ? null : state.newDrawable(activity.getResources()).mutate();
        }

        /**
         * The background LINE painted on the page: the one of the Settings list, or else the
         * first one under the content view, which is the page of the search results.
         */
        private static Drawable background(Activity activity, View page) {
            View root = find(page, idOrZero(activity, "settings_root", "id"));
            if (root != null && root.getBackground() != null) return root.getBackground();
            View content = page.findViewById(android.R.id.content);
            return content == null ? null : firstBackground(content);
        }

        private static Drawable firstBackground(View view) {
            if (!view.isShown()) return null;
            Drawable background = view.getBackground();
            if (background != null && background.getOpacity() == android.graphics.PixelFormat.OPAQUE) {
                return background;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    Drawable found = firstBackground(group.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }

        /** The first shown view with one of the ids [names], tried in order. */
        private static View find(Activity activity, View page, String... names) {
            for (String name : names) {
                View found = find(page, idOrZero(activity, name, "id"));
                if (found != null) return found;
            }
            return null;
        }

        /** The first shown view with the id [id] in the tree of [view]. */
        private static View find(View view, int id) {
            if (id == 0 || !view.isShown()) return null;
            if (view.getId() == id) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = find(group.getChildAt(i), id);
                    if (found != null) return found;
                }
            }
            return null;
        }

        /** The first shown view whose class name ends with [name], such as "TextView". */
        private static View findByClass(View view, String name) {
            if (!view.isShown()) return null;
            if (view.getClass().getName().endsWith(name)
                    && (!(view instanceof TextView) || ((TextView) view).length() > 0)) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = findByClass(group.getChildAt(i), name);
                    if (found != null) return found;
                }
            }
            return null;
        }
    }

    private static void showLicense(Activity activity) {
        TextView text = new TextView(activity);
        text.setText(LICENSE_NOTICE);
        text.setTextIsSelectable(true);
        text.setTextAppearance(id(activity, "line_user_settings_text_item_description_semantic", "style"));
        Linkify.addLinks(text, Linkify.WEB_URLS);
        int padding = dp(activity, 20);
        text.setPadding(padding, padding, padding, padding);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);

        new AlertDialog.Builder(activity)
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

    private static void restart(Activity activity) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (launch == null || launch.getComponent() == null) return;
        // LINE builds its tab list again when its main screen is created, so a new task is enough.
        // Ending the process here stopped the new task from starting on some phones.
        activity.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /** The id of LINE's resource [name]. Fails if LINE no longer has it. */
    private static int id(Context context, String name, String type) {
        int id = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (id == 0) throw new IllegalStateException("LINE has no " + type + " " + name);
        return id;
    }

    private static int idOrZero(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    private static int dimen(Context context, String name) {
        return context.getResources().getDimensionPixelSize(id(context, name, "dimen"));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
