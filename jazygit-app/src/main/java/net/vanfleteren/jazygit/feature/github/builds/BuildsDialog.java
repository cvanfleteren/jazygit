package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;

/**
 * Popup asking which workflow's builds to show: each with how its last build ended, its name and how many builds it
 * has; {@code /} filters them (ADR 131).
 */
public final class BuildsDialog {

    public static final PickDialog.Spec<BuildsPopup, Pipeline> SPEC = new PickDialog.Spec<>(
            "builds-workflows",
            BuildsPopup.class,
            popup -> Messages.get("dialog.builds.title"),
            popup -> popup.choices().map(BuildsDialog::row),
            BuildsMsg.Picked::new);

    private BuildsDialog() {}

    /**
     * The popups of the builds, which send what the user does to {@code dispatch}.
     */
    static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningToFocus(new PickDialog<>(SPEC, dispatch)));
    }

    private static PickDialog.Row<Pipeline> row(BuildsPopup.Choice choice) {
        return new PickDialog.Row<>(
                choice.pipeline(),
                List.of(
                        switch (choice.last()) {
                            case PASSED -> Cell.styled("✓", Theme.Classes.CHECKS_PASSED);
                            case FAILED -> Cell.styled("✗", Theme.Classes.CHECKS_FAILED);
                            case CANCELLED -> Cell.styled("⊘", Theme.Classes.DIM);
                            case RUNNING -> Cell.styled("○", Theme.Classes.CHECKS_RUNNING);
                        },
                        Cell.of(choice.label()),
                        Cell.styled(
                                Messages.get("dialog.builds.count", String.valueOf(choice.builds())),
                                Theme.Classes.DIM)));
    }
}
