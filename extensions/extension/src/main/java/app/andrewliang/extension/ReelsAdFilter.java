package app.andrewliang.extension;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Helper for the "[Reels] Hide sponsored reels" patch.
 *
 * <p>Facebook does not splice ads into Reels on the device. A logging build showed items reaching the
 * Reels collection only as whole fetched pages, never one at a time, with the ad already sitting in
 * the page beside the organic reels. Every client-side insertion path the patch blocks is therefore
 * beside the point: the server inlines the ad, so the only place left to stop it is where the page
 * enters the collection.
 *
 * <p>A page enters at two levels, and the patch filters both. A device round on 2026-09-19 caught
 * an ad in a page and logged {@code dropped 1 of 2} from {@link #withoutAds}. The app then showed
 * that ad as the third reel. The controller adds a section wrapper, and the wrapper holds its own
 * list of items. The screen reads that list, so a new flat collection one step later changes
 * nothing. {@link #withoutAdSections} filters the list inside each section. That list is the copy
 * that the screen reads.
 *
 * <p>Ad items all extend one base class. Its name is a Redex name that changes on every Facebook
 * release, so the patch resolves it while patching and passes it in rather than this file naming it.
 */
public final class ReelsAdFilter {

    private ReelsAdFilter() {}

    private static final String TAG = "AndrewReelsAds";

    /**
     * The same page with no ad left inside any of its sections.
     *
     * <p>This method finds the item list of a section by type and never by name. The field is
     * {@code A01} today and another name after the next Facebook release. It is the only
     * {@code List} on the wrapper that holds items.
     *
     * <p>This method removes the ads from the list in place when the list permits it. Then every
     * other holder of that list agrees with the screen. If the list refuses, this method replaces
     * the field.
     *
     * @param page        the sections about to be added to the Reels list.
     * @param adClassName binary name of the ad item base class, for example {@code X.B89}.
     */
    public static List<?> withoutAdSections(List<?> page, String adClassName) {
        if (page == null || page.isEmpty()) return page;

        int dropped = 0;
        boolean sectionEmptied = false;
        ArrayList<Object> kept = new ArrayList<>(page.size());

        for (Object section : page) {
            int removed = stripAds(section, adClassName);
            dropped += removed;

            // A section that held only ads is now empty. Remove it, so that the list does not
            // show it.
            if (removed > 0 && !holdsAnyItem(section)) {
                sectionEmptied = true;
                continue;
            }

            kept.add(section);
        }

        if (dropped == 0) return page;

        Log.i(TAG, "section filter dropped " + dropped + " item(s) from " + page.size() + " section(s)");

        return sectionEmptied ? kept : page;
    }

    /** The number of ads that this method removed from the item lists of the section. */
    private static int stripAds(Object section, String adClassName) {
        if (section == null) return 0;

        int removed = 0;

        try {
            for (Field field : section.getClass().getDeclaredFields()) {
                List<?> items = itemsOf(field, section);
                if (items == null || items.isEmpty()) continue;

                ArrayList<Object> ads = new ArrayList<>();
                for (Object item : items) {
                    if (isAd(item, adClassName)) ads.add(item);
                }
                if (ads.isEmpty()) continue;

                try {
                    // One call, so an immutable list refuses before it changes anything.
                    items.removeAll(ads);
                } catch (UnsupportedOperationException immutable) {
                    ArrayList<Object> keptItems = new ArrayList<>(items.size() - ads.size());
                    for (Object item : items) {
                        if (!isAd(item, adClassName)) keptItems.add(item);
                    }
                    field.set(section, keptItems);
                }

                removed += ads.size();
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not filter a section", t);
        }

        return removed;
    }

    /** Whether the section still holds an item. An empty section can then go. */
    private static boolean holdsAnyItem(Object section) {
        try {
            for (Field field : section.getClass().getDeclaredFields()) {
                List<?> items = itemsOf(field, section);
                if (items != null && !items.isEmpty()) return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not re-read a section", t);

            // Keep the section. A section that you cannot read is not a proven empty section.
            return true;
        }

        return false;
    }

    /** The list this field holds, or null when the field holds no list. */
    private static List<?> itemsOf(Field field, Object section) throws IllegalAccessException {
        if (!List.class.isAssignableFrom(field.getType())) return null;

        field.setAccessible(true);
        Object value = field.get(section);

        return value instanceof List ? (List<?>) value : null;
    }

    /**
     * The same items without the ads, or the very same collection when it holds none.
     *
     * <p>Returning the original untouched matters: the caller's collection may be immutable, and
     * most pages contain no ad at all, so the common case allocates nothing and changes no type.
     *
     * @param items       the page about to be added to the Reels collection.
     * @param adClassName binary name of the ad item base class, for example {@code X.B89}.
     */
    public static Collection<?> withoutAds(Collection<?> items, String adClassName) {
        if (items == null || items.isEmpty()) return items;

        boolean found = false;
        for (Object item : items) {
            if (isAd(item, adClassName)) {
                found = true;
                break;
            }
        }
        if (!found) return items;

        ArrayList<Object> kept = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!isAd(item, adClassName)) kept.add(item);
        }

        // Only when something was actually dropped, so this stays silent on an ordinary page while
        // still confirming on a device that the filter is reached and doing its job.
        Log.i(TAG, "dropped " + (items.size() - kept.size()) + " of " + items.size());

        return kept;
    }

    /** Whether [item] is the ad base class or anything extending it. */
    private static boolean isAd(Object item, String adClassName) {
        if (item == null || adClassName == null) return false;

        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            if (adClassName.equals(type.getName())) return true;
        }

        return false;
    }
}
