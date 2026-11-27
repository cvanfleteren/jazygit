package net.vanfleteren.jazygit.features.github.panes;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList.Seg;

/**
 * How the panes of the builds screen write a run (ADR 212).
 */
final class RunRows {

    /**
     * What marks a run of the branch of "mine" among those of every branch, as the builds mark it (ADR 131).
     */
    static final String MINE = "◆";

    private RunRows() {}

    /**
     * How {@code outcome} shows: a symbol in its colour.
     */
    static Seg outcome(Build.Outcome outcome) {
        return switch (outcome) {
            case PASSED -> Seg.styled("✓", Theme.Classes.CHECKS_PASSED);
            case FAILED -> Seg.styled("✗", Theme.Classes.CHECKS_FAILED);
            case CANCELLED -> Seg.styled("⊘", Theme.Classes.DIM);
            case RUNNING -> Seg.styled("○", Theme.Classes.CHECKS_RUNNING);
        };
    }

    /**
     * The row of {@code run}, made {@code since} ago, among runs whose median took {@code median}: how it ended, the
     * mark of "mine" when it is on one of {@code mine}, what it ran for, its event, how long it took against the
     * median, and how long ago.
     */
    static List<Seg> row(Build run, Option<Duration> median, Set<Sha> mine, Instant now) {
        boolean marked = mine.contains(run.sha());
        Option<Duration> took = run.duration();
        String against = took.filter(
                        any -> run.outcome() == Build.Outcome.PASSED || run.outcome() == Build.Outcome.FAILED)
                .flatMap(time -> median.map(middle -> time.minus(middle)))
                .map(RunRows::signed)
                .getOrElse("");
        return List.of(
                outcome(run.outcome()),
                marked ? Seg.styled(MINE, Theme.Classes.CURRENT_BRANCH) : Seg.of(" "),
                Seg.styled(" " + ranFor(run), marked ? Theme.Classes.CURRENT_BRANCH : Theme.Classes.PULL_REQUEST)
                        .shrinking(),
                Seg.styled(" " + event(run.event()), Theme.Classes.DIM),
                Seg.of(" " + took.map(TimeText::took).getOrElse("…")),
                Seg.styled(against.isEmpty() ? "" : " " + against, Theme.Classes.DIM),
                Seg.styled(" " + TimeText.age(Duration.between(run.createdAt(), now)), Theme.Classes.DIM));
    }

    /**
     * What {@code run} ran for: its pull request and its branch, else its branch, else its commit.
     */
    static String ranFor(Build run) {
        String branch = run.origin().branch().map(name -> name.value()).getOrElse("");
        return run.origin()
                .pullRequest()
                .map(number -> "#" + number.value() + (branch.isEmpty() ? "" : " " + branch))
                .getOrElse(() -> branch.isEmpty() ? run.sha().shortSha() : branch);
    }

    /**
     * {@code event} as short as a row has room for.
     */
    static String event(String event) {
        return switch (event) {
            case "pull_request", "pull_request_target" -> "pr";
            case "workflow_dispatch" -> "manual";
            case "merge_group" -> "queue";
            default -> event;
        };
    }

    private static String signed(Duration difference) {
        return (difference.isNegative() ? "−" : "+") + TimeText.took(difference.abs());
    }
}
