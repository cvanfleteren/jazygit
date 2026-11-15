package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.text.TimeText;
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

    /**
     * The popups of the builds, which send what the user does to {@code dispatch}.
     */
    /**
     * How many builds show: the newest 10, 20 or 50, or all those read, each picked with its first character too.
     */
    public static final PickDialog.Spec<BuildsWindowPopup, Option<Integer>> WINDOW = new PickDialog.Spec<>(
            "builds-window",
            BuildsWindowPopup.class,
            popup -> Messages.get("dialog.builds.window.title"),
            popup -> List.of(Option.of(10), Option.of(20), Option.of(50), Option.<Integer>none())
                    .map(builds -> new PickDialog.Row<>(
                            builds,
                            List.of(
                                    Cell.of(builds.map(count -> Messages.get("dialog.builds.window.last", count))
                                            .getOrElse(() -> Messages.get("dialog.builds.window.all"))),
                                    Cell.styled(
                                            popup.window().equals(builds)
                                                    ? Messages.get("dialog.builds.window.shown")
                                                    : "",
                                            Theme.Classes.DIM)),
                            Option.of(builds.map(count -> String.valueOf(count).charAt(0))
                                    .getOrElse('a')))),
            BuildsMsg.WindowPicked::new);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern(
                    Messages.get("content.builds.whenPattern"))
            .withZone(ZoneId.systemDefault());

    /**
     * Which build's jobs show on one time line: the last that is done, then each build of the tab, the newest first,
     * with how it ended, its commit, when, how long it took and its title (ADR 133).
     */
    public static final PickDialog.Spec<BuildPickPopup, Option<Long>> BUILD = new PickDialog.Spec<>(
            "builds-build",
            BuildPickPopup.class,
            popup -> Messages.get("dialog.builds.build.title"),
            popup -> popup.builds()
                    .map(build -> new PickDialog.Row<>(
                            Option.of(build.runId()),
                            List.of(
                                    outcome(Option.of(build.outcome())),
                                    Cell.styled(
                                            build.sha()
                                                    .substring(
                                                            0,
                                                            Math.min(
                                                                    7,
                                                                    build.sha().length())),
                                            Theme.Classes.SHA),
                                    Cell.styled(WHEN.format(build.createdAt()), Theme.Classes.DIM),
                                    Cell.of(build.duration().map(TimeText::took).getOrElse("–")),
                                    Cell.of(build.title()).capped(50),
                                    picked(popup, Option.of(build.runId())))))
                    .prepend(new PickDialog.Row<>(
                            Option.<Long>none(),
                            // In the column of the titles, so that the columns of the builds stay narrow.
                            List.of(
                                    Cell.of(""),
                                    Cell.of(""),
                                    Cell.of(""),
                                    Cell.of(""),
                                    Cell.of(Messages.get("dialog.builds.build.last")),
                                    picked(popup, Option.none())))),
            BuildsMsg.BuildPicked::new);

    private BuildsDialog() {}

    private static Cell picked(BuildPickPopup popup, Option<Long> run) {
        return Cell.styled(
                popup.picked().equals(run) ? Messages.get("dialog.builds.window.shown") : "", Theme.Classes.DIM);
    }

    static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningToFocus(new PickDialog<>(SPEC, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(WINDOW, dispatch)),
                PopupSlot.returningToFocus(new PickDialog<>(BUILD, dispatch)));
    }

    private static PickDialog.Row<Pipeline> row(BuildsPopup.Choice choice) {
        return new PickDialog.Row<>(
                choice.pipeline(),
                List.of(
                        outcome(choice.last()),
                        Cell.of(choice.label()),
                        Cell.styled(
                                choice.builds()
                                        .map(count -> Messages.get("dialog.builds.count", String.valueOf(count)))
                                        .getOrElse(""),
                                Theme.Classes.DIM)));
    }

    private static Cell outcome(Option<Build.Outcome> outcome) {
        return outcome.map(last -> switch (last) {
                    case PASSED -> Cell.styled("✓", Theme.Classes.CHECKS_PASSED);
                    case FAILED -> Cell.styled("✗", Theme.Classes.CHECKS_FAILED);
                    case CANCELLED -> Cell.styled("⊘", Theme.Classes.DIM);
                    case RUNNING -> Cell.styled("○", Theme.Classes.CHECKS_RUNNING);
                })
                .getOrElse(() -> Cell.styled("·", Theme.Classes.DIM));
    }
}
