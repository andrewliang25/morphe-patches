package app.andrewliang.extension;

import android.view.View;

/** Runtime part of the "[General] Enable themes on sub devices" patch for LINE. */
public final class SubDeviceThemes {

    private SubDeviceThemes() {}

    /**
     * Hides the Theme Shop button under My Themes on a sub device. LINE does not open the shop
     * there: it shows an "unsupported URL" dialog instead.
     */
    public static void hideThemeShop(boolean subDevice, View button) {
        if (subDevice) {
            button.setVisibility(View.GONE);
        }
    }
}
