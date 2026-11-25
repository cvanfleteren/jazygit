package net.vanfleteren.jazygit.features.midway.plan.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.core.repo.Plan.Step;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsAbove;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList.Row;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList.Seg;

/**
 * The steps of the operation the repository stopped in the middle of, as the commits pane lists them above its log
 * (ADR 199): the last still to come on top, down to the next one, then the one it stopped at; and the commits of the
 * log made since it started, marked done.
 */
public final class PlanRows {

    // Where a row of the log has its author's initials: a step has none, so it keeps its marks under the graph.
    private static final String NO_AUTHOR = "    ";
    // Where a row of the log has the sha of its commit, for a step that takes none.
    private static final String NO_SHA = " ".repeat(7);

    private PlanRows() {}

    /**
     * What the commits pane of {@code model} lists above its log: none while the repository is in the middle of
     * nothing whose steps git keeps.
     */
    public static Option<CommitsAbove> of(Model model) {
        return model.repo().status().toOption().flatMap(status -> status.plan().map(plan -> of(plan, status)));
    }

    static CommitsAbove of(Plan plan, RepoStatus status) {
        return new CommitsAbove(
                plan.todo().map(PlanRows::toCome).reverse().append(stopped(plan.stopped(), status.conflicts())),
                Option.of(plan.base()),
                Option.when(plan.total() > 1, () -> Messages.get("midway.plan.progress", plan.step(), plan.total())));
    }

    /**
     * A step still to come, dim: a commit on the rail of the log, {@code ○}, or the line of a step that takes none.
     */
    private static CommitsAbove.Row toCome(Step step) {
        String marks = sha(step) + NO_AUTHOR + (step.commit().isDefined() ? "○ " : "· ");
        return new CommitsAbove.Row(
                new Row(List.of(Seg.styled(marks + said(step), Theme.Classes.DIM))), step.commit(), key(step));
    }

    /**
     * The step it stopped at, {@code ✗}, with the conflicts left, or why it stopped when none are.
     */
    private static CommitsAbove.Row stopped(Step step, int conflicts) {
        Seg why = conflicts > 0
                ? Seg.styled(" · " + Messages.get("midway.plan.conflicts", conflicts), Theme.Classes.ERROR)
                : Seg.styled(" · " + Messages.get(reasonKey(step)), Theme.Classes.IN_PROGRESS);
        return new CommitsAbove.Row(
                new Row(List.of(
                        Seg.of(sha(step) + NO_AUTHOR),
                        Seg.styled("✗ ", Theme.Classes.ERROR),
                        Seg.of(said(step)).shrinking(),
                        why)),
                step.commit(),
                "stopped " + key(step));
    }

    /**
     * Why it stopped at {@code step} with no conflict left: to edit its commit, at a {@code break}, on an
     * {@code exec} that failed; else it is resolved, and waits to go on.
     */
    private static String reasonKey(Step step) {
        return switch (step.command()) {
            case "edit" -> "midway.plan.edit";
            case "break" -> "midway.plan.break";
            case "exec" -> "midway.plan.exec";
            default -> "midway.plan.ready";
        };
    }

    /**
     * What a step says after its marks: its subject, after its command when that is not {@code pick}; or, for a step
     * that takes no commit, its line.
     */
    static String said(Step step) {
        return step.commit().isEmpty()
                ? (step.command() + " " + step.text()).strip()
                : step.command().equals("pick") ? step.text() : step.command() + " " + step.text();
    }

    /**
     * A step as a sentence names it: its commit and what it says, as {@code 1466668 feat touches f}, or the line of
     * one that takes no commit.
     */
    public static String named(Step step) {
        return step.commit().map(commit -> commit.shortSha() + " ").getOrElse("") + said(step);
    }

    private static String sha(Step step) {
        return step.commit().map(commit -> commit.shortSha()).getOrElse(NO_SHA);
    }

    private static String key(Step step) {
        return step.command() + " "
                + step.commit().map(commit -> commit.value()).getOrElse(step.text());
    }
}
