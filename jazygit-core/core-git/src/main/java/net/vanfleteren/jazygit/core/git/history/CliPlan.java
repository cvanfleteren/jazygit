package net.vanfleteren.jazygit.core.git.history;

import static net.vanfleteren.jazygit.core.git.process.GitCommand.reading;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Plan;
import net.vanfleteren.jazygit.core.repo.Plan.Step;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * Reads the steps of the operation the repository stopped in the middle of from the files git keeps for it (ADR 199):
 * a rebase of the merge backend from {@code rebase-merge}, a cherry-pick or revert of several commits from
 * {@code sequencer}. A merge, a rebase of the apply backend, and anything git's files do not say, have none.
 */
public final class CliPlan {

    // The commands as git spells them in full, by the letter they may be written with.
    private static final Map<String, String> SHORT = HashMap.ofEntries(
            Map.entry("p", "pick"),
            Map.entry("r", "reword"),
            Map.entry("e", "edit"),
            Map.entry("s", "squash"),
            Map.entry("f", "fixup"),
            Map.entry("x", "exec"),
            Map.entry("b", "break"),
            Map.entry("d", "drop"),
            Map.entry("l", "label"),
            Map.entry("t", "reset"),
            Map.entry("m", "merge"),
            Map.entry("u", "update-ref"));
    // The commands whose line goes on with the commit they take.
    private static final List<String> TAKING_A_COMMIT =
            List.of("pick", "reword", "edit", "squash", "fixup", "drop", "revert");

    private CliPlan() {}

    /**
     * The steps of {@code inProgress}, which the repository of {@code worktree}, whose files are at {@code markers},
     * stopped in the middle of; {@code head} is the commit HEAD points to. None when git's files do not say them.
     * Only asked, so not logged.
     */
    public static Option<Plan> read(
            Path worktree, CliInProgress.Markers markers, InProgress inProgress, Option<Sha> head) {
        return switch (inProgress) {
            case InProgress.Rebasing rebasing -> rebase(worktree, markers.rebaseMerge(), rebasing);
            case InProgress.CherryPicking picking -> picked(worktree, markers, picking.commit(), "pick", head);
            case InProgress.Reverting reverting -> picked(worktree, markers, reverting.commit(), "revert", head);
            case InProgress.Merging merging -> Option.none();
        };
    }

    /**
     * The steps of a rebase of the merge backend: the last of those done is the one it stopped at.
     */
    private static Option<Plan> rebase(Path worktree, Path dir, InProgress.Rebasing rebasing) {
        List<Line> done = lines(contents(dir.resolve("done")).getOrElse(""));
        List<Line> todo = lines(contents(dir.resolve("git-rebase-todo")).getOrElse(""));
        Map<String, Sha> shas = resolved(worktree, done.takeRight(1).appendAll(todo));
        return done.lastOption()
                .map(stopped -> new Plan(
                        rebasing.onto(),
                        step(stopped, shas),
                        todo.map(line -> step(line, shas)),
                        rebasing.step(),
                        rebasing.total()))
                .map(plan -> withCommits(worktree, plan));
    }

    /**
     * The steps of a cherry-pick or revert: of several commits, those git keeps in {@code sequencer/todo}, the
     * first of which is the one it stopped at; of one commit, that commit alone.
     */
    private static Option<Plan> picked(
            Path worktree, CliInProgress.Markers markers, Sha commit, String command, Option<Sha> head) {
        Path sequencer = markers.sequencer();
        return contents(sequencer.resolve("todo"))
                .map(CliPlan::lines)
                .filter(List::nonEmpty)
                .flatMap(todo -> contents(sequencer.resolve("head"))
                        .flatMap(Sha::parse)
                        .map(base -> sequence(worktree, base, todo)))
                .orElse(() -> head.map(base -> withCommits(
                        worktree, new Plan(base, new Step(command, Option.of(commit), ""), List.empty(), 1, 1))));
    }

    /**
     * The steps of a sequence started at {@code base}, of which those of {@code todo} are left: the step it stopped
     * at comes after the commits made since {@code base}.
     */
    private static Plan sequence(Path worktree, Sha base, List<Line> todo) {
        Map<String, Sha> shas = resolved(worktree, todo);
        int made = GitProcess.read(
                        worktree,
                        reading("rev-list", "--count", base.value() + "..HEAD"),
                        raw -> Try.of(() -> Integer.parseInt(raw.strip()))
                                .toEither()
                                .mapLeft(failure -> new UnexpectedOutput(raw)))
                .getOrElse(0);
        return withCommits(
                worktree,
                new Plan(
                        base,
                        step(todo.head(), shas),
                        todo.tail().map(line -> step(line, shas)),
                        made + 1,
                        made + todo.size()));
    }

    /**
     * {@code plan}, with the commits of its steps, read with one {@code git log --no-walk}, which the content panel
     * shows; and with the subject of a step written without one, as by hand, or of the commit of a single
     * cherry-pick or revert, from its commit.
     */
    private static Plan withCommits(Path worktree, Plan plan) {
        List<Sha> shas =
                plan.todo().prepend(plan.stopped()).flatMap(Step::commit).distinct();
        List<Commit> commits = shas.isEmpty()
                ? List.empty()
                : CliHistory.listed(worktree, shas).getOrElse(List.empty());
        java.util.function.UnaryOperator<Step> filled = step -> step.text().isEmpty()
                ? new Step(
                        step.command(),
                        step.commit(),
                        step.commit()
                                .flatMap(sha ->
                                        commits.find(commit -> commit.sha().equals(sha)))
                                .map(commit -> commit.message().summary())
                                .getOrElse(""))
                : step;
        return new Plan(
                plan.base(), filled.apply(plan.stopped()), plan.todo().map(filled), plan.step(), plan.total(), commits);
    }

    /**
     * The full commits of the objects {@code lines} name, as git abbreviated them in a sequence: what a line names in
     * full is taken as it is, the others are asked of git together. One that git does not find is left out, and only it.
     */
    private static Map<String, Sha> resolved(Path worktree, List<Line> lines) {
        List<String> objects = lines.flatMap(Line::object).distinct();
        List<String> abbreviated = objects.filter(object -> Sha.parse(object).isEmpty());
        Map<String, Sha> full =
                HashMap.ofEntries(objects.flatMap(object -> Sha.parse(object).map(sha -> Map.entry(object, sha))));
        return abbreviated.isEmpty() ? full : full.merge(revParsed(worktree, abbreviated));
    }

    /**
     * The full commits of the abbreviated {@code objects}, read with {@code git log --no-walk --ignore-missing}, each
     * matched to the abbreviation it starts with. One that git does not find is left out, and only it: {@code rev-parse}
     * would fail them all.
     */
    private static Map<String, Sha> revParsed(Path worktree, List<String> objects) {
        List<Sha> found = GitProcess.read(
                        worktree,
                        reading(objects.prependAll(List.of("log", "--no-walk", "--ignore-missing", "--format=%H"))),
                        raw -> Either.right(
                                List.of(raw.split("\n")).map(String::strip).flatMap(Sha::parse)))
                .getOrElse(List.empty());
        return HashMap.ofEntries(objects.flatMap(
                object -> found.find(sha -> sha.value().startsWith(object)).map(sha -> Map.entry(object, sha))));
    }

    private static Step step(Line line, Map<String, Sha> shas) {
        Option<Sha> commit = line.object().flatMap(shas::get);
        // A commit git does not find is shown by what was written of it.
        String text =
                commit.isEmpty() && line.object().isDefined() ? line.object().get() + " " + line.text() : line.text();
        return new Step(line.command(), commit, text.strip());
    }

    /**
     * The lines of a list of steps, but its comments and blank lines: {@code pick <commit> # <subject>} as a rebase
     * writes them, {@code pick <commit> <subject>} as a sequence does, or a command and the rest of its line.
     */
    static List<Line> lines(String content) {
        return List.of(content.split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(CliPlan::line);
    }

    private static Line line(String text) {
        List<String> words = List.of(text.split("\\s+", 2));
        String command = SHORT.get(words.head()).getOrElse(words.head());
        String rest = words.size() > 1 ? words.get(1) : "";
        return TAKING_A_COMMIT.contains(command) ? taking(command, rest) : new Line(command, Option.none(), rest);
    }

    /**
     * A line of {@code command}, which goes on with {@code rest}: the options of a fixup, its commit, and its subject,
     * after a {@code #} as a rebase writes it.
     */
    private static Line taking(String command, String rest) {
        List<String> words = List.of(rest.split("\\s+", -1));
        // fixup -C <commit> and fixup -c <commit> take the message of the commit they fix up: the option is no step.
        List<String> afterOptions = words.dropWhile(word -> word.startsWith("-"));
        return afterOptions
                .headOption()
                .filter(object -> !object.isEmpty())
                .map(object -> new Line(
                        command,
                        Option.of(object),
                        subjectOf(afterOptions.tail().mkString(" "))))
                .getOrElse(() -> new Line(command, Option.none(), rest));
    }

    private static String subjectOf(String rest) {
        String stripped = rest.strip();
        return stripped.startsWith("#") ? stripped.substring(1).strip() : stripped;
    }

    private static Option<String> contents(Path file) {
        return Try.of(() -> Files.readString(file)).toOption();
    }

    /**
     * A line of a list of steps, as written.
     *
     * @param command what it does, in full
     * @param object  the commit it takes, as written, for those that take one
     * @param text    the subject of that commit, or the rest of the line of one that takes none
     */
    record Line(String command, Option<String> object, String text) {}
}
