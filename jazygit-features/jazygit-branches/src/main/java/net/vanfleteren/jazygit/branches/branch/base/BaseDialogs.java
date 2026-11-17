package net.vanfleteren.jazygit.branches.branch.base;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.BaseCandidate;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Row;

/**
 * The popup to pick the branch a branch is built on (ADR 143).
 */
public final class BaseDialogs {

    /**
     * Lists the branches the branch could be built on, the likeliest first, each with how many commits of the branch
     * it does not have, and how many of its own; then a row to record none, which leaves the main branch the base.
     */
    public static final PickDialog.Spec<BasePopup, Option<BranchName>> BASE = new PickDialog.Spec<>(
            "set-base",
            BasePopup.class,
            popup -> Messages.get("branches.dialog.base.title", popup.branch().value()),
            BaseDialogs::rows,
            BaseMsg.Chosen::new);

    // What marks the base the branch has now.
    private static final String CURRENT = "●";

    private BaseDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.PANE, new PickDialog<>(BASE, dispatch)));
    }

    static List<Row<Option<BranchName>>> rows(BasePopup popup) {
        return popup.candidates()
                .map(candidate -> new Row<>(
                        Option.of(candidate.name()),
                        List.of(
                                mark(popup.current().contains(candidate.name())),
                                Cell.of(candidate.name().value()),
                                Cell.styled(counts(candidate), Theme.Classes.DIM),
                                Cell.styled(tag(popup, candidate.name()), Theme.Classes.DIM))))
                .append(new Row<>(
                        Option.none(),
                        List.of(
                                mark(popup.current().isEmpty()),
                                Cell.of(popup.main()
                                        .map(main -> Messages.get("branches.dialog.base.none", main.value()))
                                        .getOrElse(() -> Messages.get("branches.dialog.base.noneWithoutMain"))),
                                Cell.of(""),
                                Cell.of(""))));
    }

    private static Cell mark(boolean current) {
        return Cell.of(current ? CURRENT : " ");
    }

    /**
     * How many commits of the branch {@code candidate} does not have, and how many of its own, leaving out a count
     * of none of its own.
     */
    private static String counts(BaseCandidate candidate) {
        return candidate.behind() == 0
                ? Messages.get("branches.dialog.base.ahead", String.valueOf(candidate.ahead()))
                : Messages.get(
                        "branches.dialog.base.aheadBehind",
                        String.valueOf(candidate.ahead()),
                        String.valueOf(candidate.behind()));
    }

    /**
     * What else is known of {@code name}: that the branch was made from it, or that it is the main branch.
     */
    private static String tag(BasePopup popup, BranchName name) {
        return popup.createdFrom().contains(name)
                ? Messages.get("branches.dialog.base.createdFrom")
                : popup.main().contains(name) ? Messages.get("branches.dialog.base.main") : "";
    }
}
