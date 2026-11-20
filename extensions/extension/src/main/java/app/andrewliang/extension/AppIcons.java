package app.andrewliang.extension;

import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Adds the collaboration icons that ship in the APK to LINE's App icon list. */
public final class AppIcons {

    private AppIcons() {}

    private static final String SECTION = "collaboration";

    /**
     * Takes the JSON of the server's App icon list (`/app-icons`) and returns it with one
     * collaboration item for each bundled collaboration launcher alias that the list does not
     * have. The item shows the launcher icon of the alias itself. On any error, it returns the
     * JSON unchanged.
     */
    public static String addBundledIcons(String json) {
        if (json == null || !LineSettings.unlockAppIcons()) return json;
        try {
            Application application = LineSettings.application();
            if (application == null) return json;

            JSONObject root = new JSONObject(json);
            Set<String> listed = new HashSet<>();
            JSONArray names = root.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                JSONObject section = root.optJSONObject(names.getString(i));
                JSONArray items = section == null ? null : section.optJSONArray("items");
                for (int j = 0; items != null && j < items.length(); j++) {
                    JSONObject item = items.optJSONObject(j);
                    if (item != null) listed.add(item.optString("name"));
                }
            }

            JSONObject section = root.optJSONObject(SECTION);
            if (section == null) {
                section = new JSONObject().put("category", new JSONObject().put("sectionTitle", "Collaboration"));
                root.put(SECTION, section);
            }
            JSONArray items = section.optJSONArray("items");
            if (items == null) {
                items = new JSONArray();
                section.put("items", items);
            }

            String packageName = application.getPackageName();
            PackageInfo info = application.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            for (ActivityInfo activity : info.activities) {
                // A launcher alias has a target activity. Its icon name is the last part of its name.
                if (activity.targetActivity == null || activity.icon == 0) continue;
                String name = activity.name.substring(activity.name.lastIndexOf('.') + 1);
                if (!name.startsWith(SECTION + "_") || listed.contains(name)) continue;
                items.put(new JSONObject()
                        .put("name", name)
                        .put("type", SECTION)
                        .put("iconUrl", "android.resource://" + packageName + "/" + activity.icon));
            }
            return root.toString();
        } catch (Exception e) {
            Log.e("AndrewAppIcons", "Could not add the bundled icons", e);
            return json;
        }
    }
}
