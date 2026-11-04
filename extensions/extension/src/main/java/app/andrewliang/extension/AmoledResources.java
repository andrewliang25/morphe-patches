package app.andrewliang.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The resource route of the "[General] AMOLED black theme" patch. See
 * {@code docs/facebook-theme-map.md}, "Route 2".
 *
 * <p>Some colour resources reach the screen only through the framework: a theme attribute, a
 * layout or a drawable names them, and no code of Facebook reads them. Thus no hook sees them.
 * While the switch is on, this class adds a {@link ResourcesLoader} (API 30) with a small resource
 * table that gives each of those colours as black. The framework reads the table like the APK's
 * own, and the table wins over the APK for an entry with the same configuration.
 *
 * <p>The patch writes the list of colours into {@link #overrides()}. The table is built when the
 * app starts, so a change of the switch needs a restart.
 */
public final class AmoledResources {

    private AmoledResources() {}

    private static final String TAG = "AndrewAmoled";

    private static final int BLACK = 0xFF000000;

    /** {@code Res_value} type of a colour {@code #aarrggbb}. */
    private static final int TYPE_INT_COLOR_ARGB8 = 0x1c;

    /** {@code ResTable_config.uiMode} for {@code -night}. */
    private static final int UI_MODE_NIGHT_YES = 0x20;

    /** {@code ResTable_typeSpec} flag: the entry changes with the UI mode. */
    private static final int CONFIG_UI_MODE = 0x1000;

    private static final int CONFIG_SIZE = 64;

    private static ResourcesLoader loader;

    /** The file stays open for as long as the process runs, because the provider reads it. */
    private static ParcelFileDescriptor table;

    /**
     * The colours that the patch found, as {@code "<id>:<night>"} in hex and joined by commas, for
     * example {@code "7f06014c:0,7f060002:1"}. Night 1 is the {@code values-night} entry. The patch
     * replaces this empty string. Keep it a plain return of one literal.
     */
    static String overrides() {
        return "";
    }

    /** Called at the start of {@code Application.onCreate}, after the super call. */
    public static void install(Application application) {
        try {
            application.registerActivityLifecycleCallbacks(new Callbacks(application));
            if (FacebookSettings.amoledTheme()) apply(application, null);
        } catch (Throwable t) {
            Log.e(TAG, "Could not add the black colour resources", t);
        }
    }

    /**
     * Adds the loader while the switch is on, and removes it while the switch is off. "Restart
     * Facebook" restarts the task in the same process, so each new Activity asks the switch again.
     *
     * <p>An Activity is a chain of contexts, and Facebook's wrappers can each hold their own
     * {@link Resources}. Each one in the chain gets the change, as does the Application.
     */
    private static void apply(Application application, Activity activity) {
        boolean on = FacebookSettings.amoledTheme();
        ResourcesLoader current = on ? loader(application) : loader;
        if (current == null) return;

        setLoader(application.getResources(), current, on);
        Context context = activity;
        while (context != null) {
            setLoader(context.getResources(), current, on);
            context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
        }
    }

    private static void setLoader(Resources resources, ResourcesLoader current, boolean on) {
        if (resources == null) return;
        if (on) resources.addLoaders(current); else resources.removeLoaders(current);
    }

    /** The loader, built on first use. Null when the patch found no colours. */
    private static synchronized ResourcesLoader loader(Application application) {
        if (loader != null) return loader;
        try {
            byte[] bytes = buildTable(application.getPackageName(), overrides());
            if (bytes == null) return null;

            File file = new File(application.getCodeCacheDir(), "andrew_amoled.arsc");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            table = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);

            ResourcesLoader newLoader = new ResourcesLoader();
            newLoader.addProvider(ResourcesProvider.loadFromTable(table, null));
            loader = newLoader;
        } catch (Throwable t) {
            Log.e(TAG, "Could not build the black colour resources", t);
        }
        return loader;
    }

    /** Sets the loader of each Activity before its theme and views read a colour. */
    private static final class Callbacks implements Application.ActivityLifecycleCallbacks {
        private final Application application;

        Callbacks(Application application) {
            this.application = application;
        }

        @Override
        public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
            try {
                apply(application, activity);
            } catch (Throwable t) {
                Log.e(TAG, "Could not set the black colour resources of " + activity, t);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityStarted(Activity activity) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    }

    /**
     * Builds a {@code resources.arsc} with one package and one type, in which every listed entry
     * is black. Gives null when the list is empty or names more than one type.
     */
    static byte[] buildTable(String packageName, String list) {
        if (list == null || list.isEmpty()) return null;

        String[] items = list.split(",");
        int[] ids = new int[items.length];
        boolean[] night = new boolean[items.length];
        int typeId = -1;
        int packageId = -1;
        int entryCount = 0;
        boolean anyDefault = false;
        boolean anyNight = false;

        for (int i = 0; i < items.length; i++) {
            String[] parts = items[i].split(":");
            ids[i] = (int) Long.parseLong(parts[0], 16);
            night[i] = "1".equals(parts[1]);
            int itemPackage = ids[i] >>> 24;
            int itemType = (ids[i] >>> 16) & 0xFF;
            if (packageId == -1) packageId = itemPackage;
            if (typeId == -1) typeId = itemType;
            if (itemPackage != packageId || itemType != typeId) return null;
            entryCount = Math.max(entryCount, (ids[i] & 0xFFFF) + 1);
            if (night[i]) anyNight = true; else anyDefault = true;
        }

        byte[] typeStrings = stringPool(typeNames(typeId));
        byte[] keyStrings = stringPool(new String[] {"amoled"});
        byte[] typeSpec = typeSpec(typeId, entryCount, ids, night);
        byte[] defaultType = anyDefault ? type(typeId, entryCount, ids, night, false) : new byte[0];
        byte[] nightType = anyNight ? type(typeId, entryCount, ids, night, true) : new byte[0];

        int packageHeader = 288;
        int packageSize = packageHeader + typeStrings.length + keyStrings.length + typeSpec.length
                + defaultType.length + nightType.length;
        byte[] valueStrings = stringPool(new String[0]);
        int tableSize = 12 + valueStrings.length + packageSize;

        ByteBuffer out = ByteBuffer.allocate(tableSize).order(ByteOrder.LITTLE_ENDIAN);

        // ResTable_header.
        out.putShort((short) 0x0002).putShort((short) 12).putInt(tableSize).putInt(1);
        out.put(valueStrings);

        // ResTable_package.
        out.putShort((short) 0x0200).putShort((short) packageHeader).putInt(packageSize);
        out.putInt(packageId);
        char[] name = new char[128];
        packageName.getChars(0, Math.min(packageName.length(), 127), name, 0);
        for (char c : name) out.putChar(c);
        out.putInt(packageHeader); // typeStrings
        out.putInt(0); // lastPublicType
        out.putInt(packageHeader + typeStrings.length); // keyStrings
        out.putInt(0); // lastPublicKey
        out.putInt(0); // typeIdOffset

        out.put(typeStrings).put(keyStrings).put(typeSpec).put(defaultType).put(nightType);
        return out.array();
    }

    /** Names for type ids 1 to {@code typeId}. Only the id counts, so the earlier names are placeholders. */
    private static String[] typeNames(int typeId) {
        String[] names = new String[typeId];
        for (int i = 0; i < typeId; i++) names[i] = "t" + (i + 1);
        names[typeId - 1] = "color";
        return names;
    }

    /** A UTF-8 {@code ResStringPool} of short ASCII strings. */
    private static byte[] stringPool(String[] strings) {
        int header = 28;
        byte[][] data = new byte[strings.length][];
        int dataSize = 0;
        for (int i = 0; i < strings.length; i++) {
            byte[] utf8 = strings[i].getBytes(StandardCharsets.UTF_8);
            data[i] = new byte[utf8.length + 3];
            data[i][0] = (byte) strings[i].length();
            data[i][1] = (byte) utf8.length;
            System.arraycopy(utf8, 0, data[i], 2, utf8.length);
            dataSize += data[i].length;
        }
        int stringsStart = header + 4 * strings.length;
        int size = (stringsStart + dataSize + 3) & ~3;

        ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0x0001).putShort((short) header).putInt(size);
        out.putInt(strings.length).putInt(0); // stringCount, styleCount
        out.putInt(1 << 8); // UTF8_FLAG
        out.putInt(strings.length == 0 ? 0 : stringsStart).putInt(0);
        int offset = 0;
        for (byte[] d : data) {
            out.putInt(offset);
            offset += d.length;
        }
        for (byte[] d : data) out.put(d);
        return out.array();
    }

    private static byte[] typeSpec(int typeId, int entryCount, int[] ids, boolean[] night) {
        int size = 16 + 4 * entryCount;
        ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0x0202).putShort((short) 16).putInt(size);
        out.put((byte) typeId).put((byte) 0).putShort((short) 0).putInt(entryCount);
        int[] flags = new int[entryCount];
        for (int i = 0; i < ids.length; i++) {
            if (night[i]) flags[ids[i] & 0xFFFF] |= CONFIG_UI_MODE;
        }
        for (int flag : flags) out.putInt(flag);
        return out.array();
    }

    /** A dense {@code ResTable_type} for the default or the night configuration. */
    private static byte[] type(int typeId, int entryCount, int[] ids, boolean[] night, boolean forNight) {
        int[] offsets = new int[entryCount];
        java.util.Arrays.fill(offsets, -1);
        int entries = 0;
        for (int i = 0; i < ids.length; i++) {
            int index = ids[i] & 0xFFFF;
            if (night[i] != forNight || offsets[index] != -1) continue;
            offsets[index] = entries * 16;
            entries++;
        }

        int header = 20 + CONFIG_SIZE;
        int entriesStart = header + 4 * entryCount;
        int size = entriesStart + 16 * entries;
        ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0x0201).putShort((short) header).putInt(size);
        out.put((byte) typeId).put((byte) 0).putShort((short) 0);
        out.putInt(entryCount).putInt(entriesStart);

        // ResTable_config: all zero, apart from its size and, for night, the UI mode.
        byte[] config = new byte[CONFIG_SIZE];
        ByteBuffer.wrap(config).order(ByteOrder.LITTLE_ENDIAN).putInt(0, CONFIG_SIZE);
        if (forNight) config[29] = (byte) UI_MODE_NIGHT_YES;
        out.put(config);

        for (int offset : offsets) out.putInt(offset);
        for (int i = 0; i < entries; i++) {
            out.putShort((short) 8).putShort((short) 0).putInt(0); // ResTable_entry, key 0
            out.putShort((short) 8).put((byte) 0).put((byte) TYPE_INT_COLOR_ARGB8).putInt(BLACK);
        }
        return out.array();
    }
}
