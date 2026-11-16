package app.andrewliang.patches.line.subdevicethemes

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import com.android.tools.smali.dexlib2.Opcode

/**
 * LINE's "use the Black theme in dark mode" check (`gk8/o.b()Z` on 26.16.0). On a sub device it
 * returns false before it reads the `THEME_AUTO_DARK_MODE` setting.
 */
internal object DarkModeGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(fieldAccess(name = "THEME_AUTO_DARK_MODE", opcode = Opcode.SGET_OBJECT)),
)

/**
 * The Settings list builder. The Themes row reads `MORETAB_SETTINGS_THEMES` and its title
 * `settings_theme_title`. The title pins the builder: the enum's own `values()` also reads the
 * constant.
 */
internal object ThemesRowFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(name = "MORETAB_SETTINGS_THEMES", opcode = Opcode.SGET_OBJECT),
        literal({ resourceId("string", "settings_theme_title") }),
    ),
)

/** The My Themes list adapter: it inflates the footer that holds the Theme Shop button. */
internal object ThemeShopFooterFingerprint : Fingerprint(
    name = "onCreateViewHolder",
    filters = listOf(
        literal({ resourceId("layout", "shop_dynamic_theme_list_theme_shop_footer") }),
        literal({ resourceId("id", "shop_button") }),
        checkCast("Landroid/widget/TextView;"),
    ),
)
