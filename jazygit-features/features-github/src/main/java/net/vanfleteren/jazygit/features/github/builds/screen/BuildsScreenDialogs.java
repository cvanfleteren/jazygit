package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;

/**
 * The popups of the builds screen (ADR 212): which runs show, and how many the chart shows.
 */
public final class BuildsScreenDialogs {

    /**
     * Which runs of the workflow highlighted show: on every branch, on the default branch, or on "mine", which says
     * why it does not apply when there is no branch for it.
     */
    public static final ChoiceDialog.Spec<FilterPopup> FILTER = new ChoiceDialog.Spec<>(
            "builds-screen-filter",
            FilterPopup.class,
            popup -> Messages.get("github.dialog.buildsScreen.filter.title"),
            List.of(
                    new Choice<FilterPopup>(
                            'e',
                            popup -> shown(
                                    popup,
                                    BuildsScreen.Filter.EVERY_BRANCH,
                                    Messages.get("github.dialog.buildsScreen.filter.every")),
                            () -> new BuildsScreenMsg.FilterPicked(BuildsScreen.Filter.EVERY_BRANCH)),
                    new Choice<FilterPopup>(
                            'd',
                            popup -> shown(
                                    popup,
                                    BuildsScreen.Filter.DEFAULT_BRANCH,
                                    popup.defaultName().isEmpty()
                                            ? Messages.get("github.dialog.buildsScreen.filter.defaultUnknown")
                                            : popup.defaultName()),
                            () -> new BuildsScreenMsg.FilterPicked(BuildsScreen.Filter.DEFAULT_BRANCH)),
                    new Choice<FilterPopup>(
                                    'm',
                                    popup -> shown(
                                            popup,
                                            BuildsScreen.Filter.MINE,
                                            popup.mine()
                                                    .map(branch -> "◆ " + branch)
                                                    .getOrElse(() ->
                                                            Messages.get("github.dialog.buildsScreen.filter.mine"))),
                                    () -> new BuildsScreenMsg.FilterPicked(BuildsScreen.Filter.MINE),
                                    (model, popup) -> popup.mine().isRight())
                            .withDetails(popup -> popup.mine()
                                    .fold(
                                            ErrorText::of,
                                            branch -> Messages.get(
                                                    "github.dialog.buildsScreen.filter.mineDetails", branch)))));

    /**
     * How many of the newest runs the chart shows: 10, 20 or 50, or as many as fit, each picked with its first
     * character too.
     */
    public static final PickDialog.Spec<ChartPopup, Option<Integer>> CHART = new PickDialog.Spec<>(
            "builds-screen-chart",
            ChartPopup.class,
            popup -> Messages.get("github.dialog.buildsScreen.chart.title"),
            popup -> List.of(Option.of(10), Option.of(20), Option.of(50), Option.<Integer>none())
                    .map(runs -> new PickDialog.Row<>(
                            runs,
                            List.of(
                                    Cell.of(runs.map(count ->
                                                    Messages.get("github.dialog.buildsScreen.chart.last", count))
                                            .getOrElse(() -> Messages.get("github.dialog.buildsScreen.chart.all"))),
                                    Cell.styled(
                                            popup.window().equals(runs)
                                                    ? Messages.get("github.dialog.buildsScreen.shown")
                                                    : "",
                                            Theme.Classes.DIM)),
                            Option.of(runs.map(count -> String.valueOf(count).charAt(0))
                                    .getOrElse('a')))),
            BuildsScreenMsg.WindowPicked::new);

    private BuildsScreenDialogs() {}

    /**
     * The popups of the screen, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new ChoiceDialog<>(FILTER, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(CHART, dispatch)));
    }

    /**
     * {@code label}, saying so when {@code filter} is what shows now.
     */
    private static String shown(FilterPopup popup, BuildsScreen.Filter filter, String label) {
        return popup.shown() == filter ? Messages.get("github.dialog.buildsScreen.filter.shown", label) : label;
    }
}
