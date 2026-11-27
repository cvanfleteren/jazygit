package net.vanfleteren.jazygit.features.changes.panes;

import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.features.changes.panes.stash.StashPanel;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;

/**
 * The panes of the repository screen this group owns (ADR 158), which it brings to the screen itself (ADR 211): the
 * files, the commits and the stashes, and the times the files pane shows. Their keys are those of the features that
 * act on them.
 */
public final class ChangesPanesFeature {

    /**
     * The three panes, and the times of the files pane, which wake the app as they change (ADR 124).
     */
    public static final Feature FEATURE = Feature.of()
            .pane(
                    Screen.REPOSITORY,
                    context -> new FilesPanel(
                            context.dispatch(),
                            () -> context.openContent().accept(FilesPanel.PANE),
                            context.highlights(),
                            context.fills()))
            .pane(
                    Screen.REPOSITORY,
                    context -> new CommitsPanel(context.dispatch(), context.highlights(), context.fills()))
            .pane(Screen.REPOSITORY, context -> new StashPanel(context.dispatch(), context.highlights()))
            .filling(KernelSlots.CLOCKING, FilesPanel::shownTimes);

    private ChangesPanesFeature() {}
}
