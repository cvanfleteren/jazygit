package net.vanfleteren.jazygit.changes.edit;

import net.vanfleteren.jazygit.ui.keys.Feature;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;

/**
 * Opening a file in the editor.
 */
public final class EditFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofKeys(EditKeys::register));

    private EditFeature() {}
}
