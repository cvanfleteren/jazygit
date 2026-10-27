package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.branch.BranchFeature;
import net.vanfleteren.jazygit.feature.cherrypick.CherryPickDialogs;
import net.vanfleteren.jazygit.feature.commit.CommitFeature;
import net.vanfleteren.jazygit.feature.discard.DiscardDialog;
import net.vanfleteren.jazygit.feature.error.ErrorDialog;
import net.vanfleteren.jazygit.feature.forcedelete.ForceDeleteDialog;
import net.vanfleteren.jazygit.feature.github.GitHubFeature;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.inprogress.InProgressDialogs;
import net.vanfleteren.jazygit.feature.repos.RecentReposDialog;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelDialogs;
import net.vanfleteren.jazygit.feature.stash.StashDialogs;
import net.vanfleteren.jazygit.feature.theme.ThemeDialog;
import net.vanfleteren.jazygit.feature.worktree.WorktreeFeature;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * Every popup of the app, with where the focus goes when it closes. At most one is open; errors come last.
 */
final class Popups {

    private final List<PopupSlot> slots;

    Popups(Consumer<Msg> dispatch) {
        this.slots = List.of(
                        BranchFeature.popups(dispatch),
                        WorktreeFeature.popups(dispatch),
                        ForceDeleteDialog.popups(dispatch),
                        GitHubFeature.popups(dispatch),
                        CommitFeature.popups(dispatch),
                        StashDialogs.popups(dispatch),
                        CherryPickDialogs.popups(dispatch),
                        DiscardDialog.popups(dispatch),
                        ThemeDialog.popups(dispatch),
                        RecentReposDialog.popups(dispatch),
                        SettingsPanelDialogs.popups(dispatch),
                        InProgressDialogs.popups(dispatch),
                        HelpDialog.popups(dispatch),
                        ErrorDialog.popups(dispatch))
                .flatMap(Function.identity());
    }

    /**
     * Whether the element {@code id} is part of a popup; never for none.
     */
    boolean owns(String id) {
        return slots.exists(slot -> slot.view().ids().contains(id));
    }

    /**
     * The popup {@code model} has open, and where the focus goes now that the frame is drawn with {@code focus}: the
     * open popup takes it, and gives it back to where it was opened from once it closes.
     */
    Rendered render(Model model, Option<String> focus) {
        List<Shown> shown = slots.map(slot -> new Shown(
                slot.view().render(model), slot.view().ids(), slot.returnTo().apply(model)));
        Option<Shown> open = shown.find(p -> p.element().isDefined());
        Option<String> moveTo = open.fold(
                // A popup that just closed gives the focus back.
                () -> shown.find(p -> focus.exists(p.ids()::contains)).map(Shown::returnTo),
                // A popup that just opened takes the focus.
                p -> focus.exists(p.ids()::contains)
                        ? Option.<String>none()
                        : p.ids().headOption());
        return new Rendered(open.flatMap(Shown::element), moveTo);
    }

    /**
     * The popups as rendered.
     *
     * @param dialog the open popup, if any
     * @param moveTo where the focus goes, if it moves
     */
    record Rendered(Option<Element> dialog, Option<String> moveTo) {}

    /**
     * A popup as rendered: its dialog while it is open.
     */
    private record Shown(Option<Element> element, List<String> ids, String returnTo) {}
}
