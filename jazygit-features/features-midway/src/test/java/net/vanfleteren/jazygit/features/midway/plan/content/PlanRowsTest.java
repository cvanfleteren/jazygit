package net.vanfleteren.jazygit.features.midway.plan.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.core.repo.Plan.Step;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsAbove;
import org.junit.jupiter.api.Test;

/**
 * The steps of an operation that stopped, as the commits pane lists them above its log (ADR 199).
 */
class PlanRowsTest {

    private static final Sha ONTO = new Sha("0".repeat(40));
    private static final Sha FIRST = new Sha("1".repeat(40));
    private static final Sha SECOND = new Sha("2".repeat(40));
    private static final Sha THIRD = new Sha("3".repeat(40));
    private static final FileEntry CONFLICTED =
            FileEntry.conflicted("f", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

    private static RepoStatus stopped(Plan plan, List<FileEntry> files) {
        return new RepoStatus(
                new Head.Detached(ONTO),
                files,
                Option.of(
                        new InProgress.Rebasing(Option.of(new BranchName("feature")), ONTO, plan.step(), plan.total())),
                List.empty(),
                Option.of(plan));
    }

    private static List<String> texts(CommitsAbove above) {
        return above.rows().map(row -> row.row().text());
    }

    @Test
    void theStepsToComeAreListedLastFirstAboveTheOneItStoppedAtWithItsConflicts() {
        Plan plan = new Plan(
                ONTO,
                Step.pick(FIRST, "feat touches f"),
                List.of(
                        new Step("fixup", Option.of(SECOND), "fixup! feat touches f"),
                        new Step("exec", Option.none(), "make test"),
                        Step.pick(THIRD, "feat 5")),
                2,
                5);

        CommitsAbove above = PlanRows.of(plan, stopped(plan, List.of(CONFLICTED)));

        assertEquals(
                List.of(
                        "3333333    ○ feat 5",
                        "           · exec make test",
                        "2222222    ○ fixup fixup! feat touches f",
                        "1111111    ✗ feat touches f · 1 conflict"),
                texts(above));
        assertEquals(
                List.of(Option.of(THIRD), Option.none(), Option.of(SECOND), Option.of(FIRST)),
                above.rows().map(CommitsAbove.Row::commit));
        assertEquals(Option.of(ONTO), above.doneAbove());
        assertEquals(Option.of("2/5"), above.progress());
    }

    @Test
    void withNoConflictLeftTheStepItStoppedAtSaysWhy() {
        assertEquals(
                List.of("1111111    ✗ edit feat 1 · stopped to edit"),
                texts(planned(only(new Step("edit", Option.of(FIRST), "feat 1")), List.empty())));
        assertEquals(
                List.of("           ✗ break · stopped at break"),
                texts(planned(only(new Step("break", Option.none(), "")), List.empty())));
        assertEquals(
                List.of("           ✗ exec make test · the command failed"),
                texts(planned(only(new Step("exec", Option.none(), "make test")), List.empty())));
        assertEquals(
                List.of("1111111    ✗ feat 1 · ready to continue"),
                texts(planned(only(Step.pick(FIRST, "feat 1")), List.empty())));
    }

    @Test
    void aSingleStepSaysNoProgress() {
        Plan plan = new Plan(ONTO, Step.pick(FIRST, "feat 1"), List.empty(), 1, 1);

        assertEquals(
                Option.none(), PlanRows.of(plan, stopped(plan, List.empty())).progress());
    }

    private static Plan only(Step stopped) {
        return new Plan(ONTO, stopped, List.empty(), 3, 4);
    }

    /**
     * The rows of {@code plan}, stopped with {@code files} changed.
     */
    private static CommitsAbove planned(Plan plan, List<FileEntry> files) {
        return PlanRows.of(plan, stopped(plan, files));
    }
}
