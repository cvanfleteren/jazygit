package net.vanfleteren.jazygit.features.changes.cherrypick;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * Copying commits and cherry-picking them onto the branch checked out.
 */
public final class CherryPickFeature {

    /**
     * Escape lets go of the commit copied to cherry-pick. It only gets here when nothing else, like a dialog, took it.
     */
    private static final FeatureAction GLOBAL_KEY = FeatureAction.ofKeys(keys -> keys.global(
            Keymap.Global.Dispatching.ofEscape("app.help.global.cherryPickDrop", CherryPickMsg.Dropped::new)));

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE =
            Feature.of(new FeatureAction(CherryPickKeys::register, CherryPickDialogs::popups), GLOBAL_KEY);

    private CherryPickFeature() {}
}
