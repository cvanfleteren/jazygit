package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;

/**
 * Opening a file in an editor (ADR 145).
 */
public final class EditFeature {

    /**
     * What the app knows of it: its keys, the menu of the editors that open files, and that it opens the files other
     * features ask for (ADR 206).
     */
    public static final Feature FEATURE = Feature.of(
                    FeatureAction.ofKeys(EditKeys::register),
                    FeatureAction.ofPopups(dispatch ->
                            List.of(PopupSlot.returningToFocus(new PickDialog<>(EditDialog.MENU, dispatch)))))
            .reacting(EditUpdate::reacting);

    private EditFeature() {}
}
