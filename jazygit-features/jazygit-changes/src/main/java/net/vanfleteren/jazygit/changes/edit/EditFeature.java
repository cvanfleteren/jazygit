package net.vanfleteren.jazygit.changes.edit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * Opening a file in an editor (ADR 145).
 */
public final class EditFeature {

    /**
     * What the app knows of it: its keys and the menu of the editors that open files.
     */
    public static final Feature FEATURE = Feature.of(
            FeatureAction.ofKeys(EditKeys::register),
            FeatureAction.ofPopups(
                    dispatch -> List.of(PopupSlot.returningToFocus(new PickDialog<>(EditDialog.MENU, dispatch)))));

    private EditFeature() {}
}
