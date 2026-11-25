package net.vanfleteren.jazygit.core.git.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.history.CliPlan.Line;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.core.repo.Plan.Step;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Reading the steps of an operation that stopped from git's files (ADR 199).
 */
class CliPlanTest {

    private static final String FULL = "1466668bd57f41d8df85cdd2296fb40076497ebf";

    @TempDir
    Path repo;

    @Test
    void aLineOfARebaseIsItsCommandItsCommitAndItsSubject() {
        assertEquals(
                List.of(new Line("pick", Option.of(FULL), "feat touches f")),
                CliPlan.lines("pick " + FULL + " # feat touches f\n"));
    }

    @Test
    void aLineOfASequenceHasItsSubjectWithoutAHash() {
        assertEquals(
                List.of(new Line("pick", Option.of("df33d9f"), "feat touches f")),
                CliPlan.lines("pick df33d9f feat touches f\n"));
    }

    @Test
    void theCommandsAreNamedInFullAndTheOptionsOfAFixupLeftOut() {
        assertEquals(
                List.of(
                        new Line("fixup", Option.of(FULL), "a"),
                        new Line("fixup", Option.of(FULL), "b"),
                        new Line("squash", Option.of(FULL), "c")),
                CliPlan.lines("f " + FULL + " # a\nfixup -C " + FULL + " # b\ns " + FULL + " # c\n"));
    }

    @Test
    void aLineThatTakesNoCommitIsTheRestOfIt() {
        assertEquals(
                List.of(
                        new Line("exec", Option.none(), "make test"),
                        new Line("break", Option.none(), ""),
                        new Line("label", Option.none(), "onto"),
                        new Line("merge", Option.none(), "-C " + FULL + " topic # Merge topic")),
                CliPlan.lines("x make test\nbreak\nlabel onto\nmerge -C " + FULL + " topic # Merge topic\n"));
    }

    @Test
    void commentsAndBlankLinesAreNoSteps() {
        assertEquals(
                List.of(new Line("pick", Option.of(FULL), "a")),
                CliPlan.lines("\n# Rebase 5c1ef1d..2b5a554 onto 5c1ef1d\npick " + FULL + " # a\n\n#  more\n"));
    }

    @Test
    void aRebaseThatStoppedOnConflictsHasTheCommitItStoppedAtAndThoseToCome() throws Exception {
        diverged();

        gitAnyExit("rebase", "main");

        assertEquals(
                Option.of(new Plan(
                        sha("main"),
                        Step.pick(sha("feature~1"), "feat touches f"),
                        List.of(Step.pick(sha("feature"), "feat 5")),
                        3,
                        4)),
                plan());
    }

    @Test
    void anInteractiveRebaseListsItsCommandsAsTheyWereWritten() throws Exception {
        diverged();

        gitAnyExit(
                "-c",
                "sequence.editor=sed -i -e '2s/^pick/fixup/' -e '3a exec true' -e '1s/^pick/edit/'",
                "rebase",
                "-i",
                "main");

        assertEquals(
                Option.of(new Plan(
                        sha("main"),
                        new Step("edit", Option.of(sha("feature~3")), "feat 1"),
                        List.of(
                                new Step("fixup", Option.of(sha("feature~2")), "feat 2"),
                                Step.pick(sha("feature~1"), "feat touches f"),
                                new Step("exec", Option.none(), "true"),
                                Step.pick(sha("feature"), "feat 5")),
                        1,
                        5)),
                plan());
    }

    @Test
    void aStepWrittenWithoutItsSubjectHasItReadFromGit() throws Exception {
        diverged();

        gitAnyExit("-c", "sequence.editor=sed -i -e 's/ # .*//'", "rebase", "-i", "main");

        assertEquals(Option.of(Step.pick(sha("feature~1"), "feat touches f")), plan().map(Plan::stopped));
        assertEquals(Option.of(List.of(Step.pick(sha("feature"), "feat 5"))), plan().map(Plan::todo));
    }

    @Test
    void aSequenceOfCherryPicksCountsTheCommitsPickedSinceItStarted() throws Exception {
        diverged();
        git("switch", "-q", "main");
        Sha start = sha("HEAD");

        gitAnyExit("cherry-pick", "main..feature");

        assertEquals(
                Option.of(new Plan(
                        start,
                        Step.pick(sha("feature~1"), "feat touches f"),
                        List.of(Step.pick(sha("feature"), "feat 5")),
                        3,
                        4)),
                plan());
    }

    @Test
    void aSingleCherryPickIsTheOneStep() throws Exception {
        diverged();
        git("switch", "-q", "main");
        Sha head = sha("HEAD");

        gitAnyExit("cherry-pick", "feature~1");

        assertEquals(
                Option.of(new Plan(head, Step.pick(sha("feature~1"), "feat touches f"), List.empty(), 1, 1)), plan());
    }

    @Test
    void theCommitsOfTheStepsAreReadToShowThem() throws Exception {
        diverged();
        gitAnyExit("rebase", "main");
        CliInProgress.Markers markers = CliInProgress.markers(repo).get();

        Plan plan = CliPlan.read(repo, markers, CliInProgress.read(markers).get(), Option.of(sha("HEAD")))
                .get();

        assertEquals(
                Option.of("feat touches f"),
                plan.commit(sha("feature~1")).map(commit -> commit.message().summary()));
        assertEquals(
                Option.of("feat 5"),
                plan.commit(sha("feature")).map(commit -> commit.message().summary()));
        assertEquals(2, plan.commits().size());
    }

    @Test
    void aMergeHasNoPlan() throws Exception {
        diverged();
        git("switch", "-q", "main");

        gitAnyExit("merge", "feature");

        assertEquals(Option.none(), plan());
    }

    @Test
    void aSequenceOfRevertsHasItsRevertsAsSteps() throws Exception {
        diverged();
        commit("f", "later\n", "later touches f");
        Sha start = sha("HEAD");
        Sha touches = sha("HEAD~2");
        Sha five = sha("HEAD~1");

        gitAnyExit("revert", "--no-edit", touches.value(), five.value());

        assertEquals(
                Option.of(new Plan(
                        start,
                        new Step("revert", Option.of(touches), "feat touches f"),
                        List.of(new Step("revert", Option.of(five), "feat 5")),
                        1,
                        2)),
                plan());
    }

    @Test
    void aRebaseOfTheApplyBackendHasNoPlan() throws Exception {
        diverged();

        gitAnyExit("rebase", "--apply", "main");

        assertEquals(Option.none(), plan());
    }

    @Test
    void aRebaseStoppedByACommandThatFailedStoppedAtThatCommand() throws Exception {
        diverged();

        gitAnyExit("-c", "sequence.editor=sed -i -e '1a exec false'", "rebase", "-i", "main");

        assertEquals(Option.of(new Step("exec", Option.none(), "false")), plan().map(Plan::stopped));
    }

    @Test
    void aRebaseStoppedAtABreakStoppedThere() throws Exception {
        diverged();

        gitAnyExit("-c", "sequence.editor=sed -i -e '1a break'", "rebase", "-i", "main");

        assertEquals(Option.of(new Step("break", Option.none(), "")), plan().map(Plan::stopped));
    }

    @Test
    void aCommitGitDoesNotFindCostsOnlyItsOwnStep() throws Exception {
        diverged();
        gitAnyExit("rebase", "main");
        Path todo = repo.resolve(".git/rebase-merge/git-rebase-todo");
        Files.writeString(todo, Files.readString(todo) + "pick 0123456789abcdef0123456789abcdef01234567 # ghost\n");
        CliInProgress.Markers markers = CliInProgress.markers(repo).get();

        Plan plan = CliPlan.read(repo, markers, CliInProgress.read(markers).get(), Option.of(sha("HEAD")))
                .get();

        assertEquals(
                HashSet.of(sha("feature~1"), sha("feature")),
                plan.commits().map(Commit::sha).toSet());
    }

    @Test
    void anAbbreviatedCommitGitDoesNotFindCostsOnlyItsOwnStep() throws Exception {
        diverged();
        git("switch", "-q", "main");
        gitAnyExit("cherry-pick", "main..feature");
        Path todo = repo.resolve(".git/sequencer/todo");
        Files.writeString(todo, Files.readString(todo) + "pick deadbee ghost\n");

        Option<Plan> plan = plan();

        assertEquals(Option.of(Step.pick(sha("feature~1"), "feat touches f")), plan.map(Plan::stopped));
        assertEquals(
                Option.of(
                        List.of(Step.pick(sha("feature"), "feat 5"), new Step("pick", Option.none(), "deadbee ghost"))),
                plan.map(Plan::todo));
    }

    private Option<Plan> plan() {
        CliInProgress.Markers markers = CliInProgress.markers(repo).get();
        InProgress inProgress = CliInProgress.read(markers).get();
        return CliPlan.read(repo, markers, inProgress, Option.of(sha("HEAD"))).map(Plan::steps);
    }

    /**
     * Main and feature parted at base; feature has four commits, the third of which changes f, as main does.
     */
    private void diverged() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        commit("f", "base\n", "base");
        git("switch", "-qc", "feature");
        commit("g1", "1\n", "feat 1");
        commit("g2", "2\n", "feat 2");
        commit("f", "feature\n", "feat touches f");
        commit("g4", "4\n", "feat 5");
        git("switch", "-q", "main");
        commit("f", "main\n", "main touches f");
        git("switch", "-q", "feature");
    }

    private void commit(String file, String content, String subject) throws Exception {
        Files.writeString(repo.resolve(file), content);
        git("add", file);
        git("commit", "-qm", subject);
    }

    private Sha sha(String revision) {
        try {
            return new Sha(git("rev-parse", revision).strip());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void gitAnyExit(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .environment("GIT_EDITOR", "true")
                .readOutput(true)
                .execute();
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}
