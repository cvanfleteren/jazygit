package net.vanfleteren.jazygit.core.git.history;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitArgs;
import net.vanfleteren.jazygit.core.git.process.GitCommand;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.core.git.process.GitError.UnknownBranch;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.git.process.GitOutput;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.process.Parallel;
import net.vanfleteren.jazygit.core.repo.*;

/**
 * The {@link History} of a repository, through {@code git rev-parse} and {@code git log}. The checkout and the
 * commits find a commit through {@link #resolve}, and the branches count through {@link #count}.
 */
public final class CliHistory implements History {

    // sha, parents, author name, author email, author time, subject, ref names, raw message
    private static final List<String> FIELDS = List.of("%H", "%P", "%an", "%ae", "%at", "%s", "%D", "%B");
    private static final String TAG_PREFIX = "tag: ";
    private static final String FORMAT = FIELDS.mkString(GitOutput.PRETTY_FIELD_SEPARATOR);
    // The mark %m prints for a boundary commit of git log --boundary.
    private static final String BOUNDARY = "-";
    private static final String MARKED_FORMAT = "%m" + GitOutput.PRETTY_FIELD_SEPARATOR + FORMAT;
    // A reflog entry's selector, which --date=unix makes its time, and its subject, then the fields of its commit.
    private static final String REFLOG_FORMAT =
            "%gd" + GitOutput.PRETTY_FIELD_SEPARATOR + "%gs" + GitOutput.PRETTY_FIELD_SEPARATOR + FORMAT;
    // How git config writes false: https://git-scm.com/docs/git-config#Documentation/git-config.txt-boolean
    private static final Set<String> FALSE = HashSet.of("false", "no", "off", "0");

    private final Path worktree;

    public CliHistory(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * The commit {@code revision} names; none when it names nothing, e.g. {@code HEAD} in a repository
     * without commits.
     */
    public static Either<GitFailure, Option<Sha>> resolve(Path worktree, String revision) {
        // rev-parse --verify --quiet exits with 1, saying nothing, when the revision names nothing.
        // After --end-of-options, a revision typed with a leading - is still read as one.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(
                                "rev-parse", "--verify", "--quiet", "--end-of-options", revision + "^{commit}"))
                .flatMap(answer -> answer.fold(
                        () -> Either.right(Option.none()),
                        found -> GitOutput.sha(found.stdout())
                                .map(Option::of)
                                .mapLeft(error -> new GitFailure(error, found.log()))));
    }

    /**
     * The commits of the log of HEAD that {@code query} finds, newest first; none in a repository without commits.
     * They are left as git lists them: with commits left out, merges and what they brought in no longer join up.
     */
    @Override
    public Either<GitError, List<Commit>> search(CommitQuery query) {
        return resolve(worktree, "HEAD")
                .mapLeft(GitFailure::error)
                .flatMap(head -> head.fold(
                        () -> Either.right(List.empty()),
                        tip -> read(worktree, filters(query).append(tip.value()), query.paths(), MAX_COMMITS)));
    }

    /**
     * The options of {@code git log} that keep the commits {@code query} finds, apart from its paths. The texts
     * are found as they are, not as regular expressions. git keeps a commit of any of the authors, whose message has
     * the phrase.
     */
    static List<String> filters(CommitQuery query) {
        return List.of("--fixed-strings")
                .appendAll(Option.when(query.ignoresCase(), "--regexp-ignore-case"))
                .appendAll(query.message().map(message -> "--grep=" + message))
                .appendAll(query.authors().map(author -> "--author=" + author))
                .appendAll(query.pickaxe().map(text -> "-S" + text));
    }

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    @Override
    public Either<GitError, List<Commit>> log(BranchRef branch) {
        return tip(worktree, branch).flatMap(tip -> Either.narrow(commits(tip)));
    }

    /**
     * The newest commits reachable from {@code start}, newest first, laid out by {@link CommitGraph#arrange}.
     */
    @Override
    public Either<GitError, List<Commit>> commits(Sha start) {
        return selected(worktree, List.of(start.value()), MAX_COMMITS);
    }

    /**
     * The commits only on {@code branch} and the ones only on {@code other}: at most {@link #MAX_COMMITS} of
     * each, and how many there are in all.
     */
    @Override
    public Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other) {
        return tips(worktree, branch, other)
                .flatMap(tips -> tips.apply((ours, theirs) -> Either.narrow(counts(worktree, ours, theirs)
                        .flatMap(counts -> selected(worktree, List.of(ours.value(), "^" + theirs.value()), MAX_COMMITS)
                                .flatMap(ahead -> selected(
                                                worktree, List.of(theirs.value(), "^" + ours.value()), MAX_COMMITS)
                                        .map(behind -> new Divergence(
                                                new Divergence.Side(ahead, counts._1),
                                                new Divergence.Side(behind, counts._2))))))));
    }

    /**
     * The commits {@code ours} and {@code theirs} are at, read in one {@code for-each-ref} over their full refs (ADR 49).
     */
    private static Either<GitError, Tuple2<Sha, Sha>> tips(Path worktree, BranchRef ours, BranchRef theirs) {
        return Either.<GitError, Map<String, Sha>>narrow(GitProcess.read(
                        worktree,
                        GitCommand.reading(List.of(
                                "for-each-ref",
                                "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(objectname)",
                                GitArgs.revision(ours),
                                GitArgs.revision(theirs))),
                        CliHistory::refs))
                .flatMap(found -> found.get(GitArgs.revision(ours))
                        .toEither(() -> (GitError) new UnknownBranch(ours))
                        .flatMap(sha -> found.get(GitArgs.revision(theirs))
                                .toEither(() -> (GitError) new UnknownBranch(theirs))
                                .map(other -> Tuple.of(sha, other))));
    }

    /**
     * The commit of each ref in {@code raw}, by its full name, a line per ref as {@code for-each-ref} lists it: its
     * name, then the object it points to, which for a branch is its commit. A pattern of {@code for-each-ref} also
     * matches the refs below it, so the refs asked for are looked up by their exact name.
     */
    static Either<UnexpectedOutput, Map<String, Sha>> refs(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 2)
                                .flatMap(fields ->
                                        GitOutput.sha(fields.get(1)).map(sha -> Tuple.of(fields.get(0), sha)))))
                .map(HashMap::ofEntries);
    }

    @Override
    public Either<GitError, Ahead> ahead(Sha tip, BranchRef base, Sha baseTip) {
        return ahead(worktree, tip, base, baseTip, MAX_COMMITS);
    }

    /**
     * The commits from {@code tip} that are not on {@code base}, whose tip is {@code baseTip}, at most {@code max}, and
     * the commit they forked from, read in one {@code git log --boundary} where it can (ADR 56): one more commit than
     * {@code max} tells whether there are more.
     */
    static Either<GitError, Ahead> ahead(Path worktree, Sha tip, BranchRef base, Sha baseTip, int max) {
        return walk(worktree, tip, baseTip, max + 1).flatMap(walk -> {
            boolean more = walk.commits().size() > max;
            List<Commit> commits = CommitGraph.arrange(walk.commits().take(max));
            return forkPoint(worktree, tip, baseTip, walk, more).map(fork -> new Ahead(tip, base, commits, more, fork));
        });
    }

    /**
     * The commit {@code tip} forked from {@code baseTip} at, from what {@code walk} found where that tells: every
     * merge-base is a boundary commit of a walk that was not cut short, as the commits after it on a path from the tip
     * are not on the base. So one boundary commit is the fork point; none, with commits walked, means the histories
     * never met; and without commits walked, the tip is on the base, and is the fork point itself. Several boundary
     * commits, or a walk cut short, are asked of {@code git merge-base}.
     */
    private static Either<GitError, Option<Commit>> forkPoint(
            Path worktree, Sha tip, Sha baseTip, Walk walk, boolean more) {
        if (more || walk.boundary().size() > 1) {
            return forkPoint(worktree, tip, baseTip);
        } else if (walk.commits().isEmpty()) {
            return read(worktree, List.of("--no-walk", tip.value()), List.empty(), 1)
                    .map(List::headOption);
        } else {
            return Either.right(walk.boundary().headOption());
        }
    }

    /**
     * The best common ancestor of {@code ours} and {@code theirs}; none for histories that never met.
     */
    private static Either<GitError, Option<Commit>> forkPoint(Path worktree, Sha ours, Sha theirs) {
        // merge-base exits with 1, saying nothing, when there is no common ancestor.
        return GitProcess.ask(worktree, GitCommand.reading("merge-base", ours.value(), theirs.value()))
                .mapLeft(GitFailure::error)
                .flatMap(answer -> answer.fold(
                        () -> Either.right(Option.none()),
                        found -> Either.<GitError, Sha>narrow(GitOutput.sha(found.stdout()))
                                .flatMap(sha -> read(worktree, List.of("--no-walk", sha.value()), List.empty(), 1))
                                .map(List::headOption)));
    }

    /**
     * The subject of the oldest commit on {@code branch} that is not on {@code base}; none when there is none.
     */
    @Override
    public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
        return tips(worktree, branch, base)
                .flatMap(tips -> tips.apply((ours, theirs) -> Either.narrow(GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                "log",
                                "--no-color",
                                "--reverse",
                                "--format=%s",
                                ours.value(),
                                "^" + theirs.value(),
                                "--"),
                        raw -> Either.right(GitOutput.nonBlankLines(raw).headOption())))));
    }

    /**
     * The commits of {@code branch} from {@code oldest} up to its tip, read with {@code git rev-list --parents
     * --topo-order}, which lists each with its parents, and before its parents; how many of them are on
     * {@code upstream}, from how many are not; and the merge {@code oldest} came in through, when its parent is not
     * on the line of {@code branch} itself.
     */
    @Override
    public Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream) {
        List<String> revisions = List.of(GitArgs.revision(new BranchRef.Local(branch)), "^" + oldest.value() + "^");
        return Either.narrow(GitProcess.read(
                        worktree,
                        GitCommand.reading(List.of("rev-list", "--parents", "--topo-order")
                                .appendAll(revisions)
                                .append("--")),
                        CliHistory::parents)
                .flatMap(commits -> upstream.map(
                                remote -> count(worktree, revisions.append(GitArgs.not(remote.tracking()))))
                        .getOrElse(() -> Either.right(commits.size()))
                        .map(unpushed -> new CommitRange(
                                commits.size(),
                                commits.count(commit -> commit.size() > 2),
                                commits.size() - unpushed,
                                mergedThrough(commits, oldest)))));
    }

    /**
     * The lines of {@code git rev-list --parents}: each commit, then its parents. What is not a full sha fails the
     * read, as these are handed back to git (ADR 40).
     */
    static Either<UnexpectedOutput, List<List<Sha>>> parents(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> Either.sequenceRight(
                                        List.of(line.split(" ")).map(GitOutput::sha))
                                .map(Seq::toList)))
                .map(Seq::toList);
    }

    /**
     * The merge on the line of the branch, which its tip follows through first parents, that {@code oldest} came in
     * through; none when the parent of {@code oldest}, where the branch is moved back to, is on that line.
     * {@code commits} run from the tip to {@code oldest}, each a sha followed by its parents, and before its parents.
     * The line leaves them at one commit, the first parent of the last commit on it, which is the parent of
     * {@code oldest} only when that is on the line. The merge is the last commit on the line that has {@code oldest}
     * as an ancestor.
     */
    private static Option<Sha> mergedThrough(List<List<Sha>> commits, Sha oldest) {
        Map<Sha, List<Sha>> parents = HashMap.ofEntries(commits.map(commit -> Tuple.of(commit.head(), commit.tail())));
        Set<Sha> descendants = commits.reverse()
                .foldLeft(
                        HashSet.of(oldest),
                        (found, commit) -> commit.tail().exists(found::contains) ? found.add(commit.head()) : found);
        List<Sha> line = List.unfoldRight(
                commits.headOption().map(List::head),
                next -> next.filter(parents::containsKey)
                        .map(sha -> Tuple.of(sha, parents.get(sha).flatMap(List::headOption))));
        Option<Sha> leftAt = line.lastOption().flatMap(parents::get).flatMap(List::headOption);
        return leftAt.equals(parents.get(oldest).flatMap(List::headOption))
                ? Option.none()
                : line.filter(descendants::contains).lastOption();
    }

    /**
     * How many commits are on {@code branch} and not on {@code base}, counting no further than {@code max}.
     */
    @Override
    public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
        return count(worktree, List.of("--max-count=" + max, GitArgs.revision(branch), GitArgs.not(base)));
    }

    @Override
    public Either<GitError, Set<Sha>> onlyOn(Sha tip, List<Sha> others, int max) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(List.of("rev-list", "--max-count=" + max, GitArgs.revision(new Revision.Commit(tip)))
                        .appendAll(others.map(other -> GitArgs.not(new Revision.Commit(other))))
                        .append("--")),
                raw -> Either.right(GitOutput.nonBlankLines(raw).map(Sha::new).toSet()));
    }

    /**
     * How many commits {@code revisions} select.
     */
    public static Either<GitError, Integer> count(Path worktree, List<String> revisions) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(
                        List.of("rev-list", "--count").appendAll(revisions).append("--")),
                raw -> GitOutput.number(raw.strip()).map(Long::intValue));
    }

    private static Either<GitError, Sha> tip(Path worktree, BranchRef branch) {
        return Either.<GitError, Option<Sha>>narrow(
                        resolve(worktree, GitArgs.revision(branch)).mapLeft(GitFailure::error))
                .flatMap(tip -> tip.toEither(() -> new UnknownBranch(branch)));
    }

    /**
     * How many commits are only on {@code ours}, and how many only on {@code theirs}.
     */
    private static Either<GitError, Tuple2<Integer, Integer>> counts(Path worktree, Sha ours, Sha theirs) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(
                        "rev-list",
                        "--left-right",
                        "--count",
                        GitArgs.symmetric(new Revision.Commit(ours), new Revision.Commit(theirs)),
                        "--"),
                CliHistory::counts);
    }

    /**
     * The output of {@code rev-list --left-right --count}: the two counts, separated by a tab.
     */
    static Either<UnexpectedOutput, Tuple2<Integer, Integer>> counts(String raw) {
        return GitOutput.fields(raw.strip(), "\t", 2)
                .flatMap(counts -> GitOutput.number(counts.get(0))
                        .flatMap(left -> GitOutput.number(counts.get(1))
                                .map(right -> Tuple.of(left.intValue(), right.intValue()))));
    }

    /**
     * The commits {@code shas}, as {@code git log --no-walk --ignore-missing} lists them: those git does not find left
     * out, rather than failing the whole read, as a list of steps edited by hand may name one.
     */
    static Either<GitError, List<Commit>> listed(Path worktree, List<Sha> shas) {
        return read(
                worktree,
                shas.map(Sha::value).prepend("--ignore-missing").prepend("--no-walk"),
                List.empty(),
                shas.size());
    }

    /**
     * The newest commits {@code revisions} select, at most {@code max}, newest first, laid out by
     * {@link CommitGraph#arrange}.
     */
    private static Either<GitError, List<Commit>> selected(Path worktree, List<String> revisions, int max) {
        return read(worktree, revisions, List.empty(), max).map(CommitGraph::arrange);
    }

    /**
     * The newest commits {@code arguments} select that change one of {@code paths}, or any commit when there are
     * none, at most {@code max}, as {@code git log} lists them.
     */
    private static Either<GitError, List<Commit>> read(
            Path worktree, List<String> arguments, List<String> paths, int max) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(List.of("log", "--no-color", "--no-show-signature", "-z", "--max-count=" + max)
                        .append("--format=" + FORMAT)
                        .appendAll(arguments)
                        .append("--")
                        .appendAll(paths)),
                CliHistory::parse);
    }

    /**
     * The newest commits from {@code tip} that are not on {@code baseTip}, at most {@code max}, as {@code git log}
     * lists them, and the boundary commits it stopped at: the commits on the base whose child it listed, and, when
     * {@code max} cut it short, those it would have listed next.
     */
    private static Either<GitError, Walk> walk(Path worktree, Sha tip, Sha baseTip, int max) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(List.of(
                        "log",
                        "--no-color",
                        "--no-show-signature",
                        "-z",
                        "--boundary",
                        "--max-count=" + max,
                        "--format=" + MARKED_FORMAT,
                        tip.value(),
                        "^" + baseTip.value(),
                        "--")),
                CliHistory::parseWalk);
    }

    /**
     * Parses {@code git log --boundary -z} output in the format of {@link #MARKED_FORMAT}: each commit after its
     * mark, {@code -} for a boundary commit.
     */
    static Either<UnexpectedOutput, Walk> parseWalk(String raw) {
        return Either.sequenceRight(GitOutput.records(raw)
                        .map(record -> GitOutput.fields(record, 2)
                                .flatMap(marked -> commit(marked.get(1))
                                        .map(commit -> Tuple.of(marked.get(0).equals(BOUNDARY), commit)))))
                .map(Seq::toList)
                .map(marked -> new Walk(
                        marked.filter(commit -> !commit._1).map(Tuple2::_2),
                        marked.filter(Tuple2::_1).map(Tuple2::_2)));
    }

    /**
     * Parses {@code git log -z} output in the format of {@link #FIELDS}.
     */
    static Either<UnexpectedOutput, List<Commit>> parse(String raw) {
        return Either.sequenceRight(GitOutput.records(raw).map(CliHistory::commit))
                .map(Seq::toList);
    }

    private static Either<UnexpectedOutput, Commit> commit(String record) {
        return GitOutput.fields(record, FIELDS.size())
                .flatMap(f -> GitOutput.sha(f.get(0))
                        .flatMap(sha -> GitOutput.epochSeconds(f.get(4))
                                .map(time -> new Commit(
                                        sha,
                                        f.get(2),
                                        f.get(3),
                                        time,
                                        new CommitMessage(f.get(5), extendedMessage(f.get(7))),
                                        List.of(f.get(1).split(" ")).flatMap(Sha::parse),
                                        tags(f.get(6))))));
    }

    /**
     * The tags among the ref names of a commit, as {@code %D} prints them: {@code HEAD -> main, tag: v1}.
     */
    static List<Tag> tags(String refNames) {
        return List.of(refNames.split(","))
                .map(String::strip)
                .filter(name -> name.startsWith(TAG_PREFIX))
                .map(name -> new Tag(name.substring(TAG_PREFIX.length())));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        return List.of(fullMessage.split("\\R\\s*\\R", 2))
                .drop(1)
                .headOption()
                .map(String::strip)
                .getOrElse("");
    }

    @Override
    public Either<GitError, List<StackLayer.Commits>> stackCommits(List<StackLayer> layers, Revision base) {
        return Either.sequenceRight(layers.map(layer -> layerCommits(layer, base)))
                .map(List::ofAll);
    }

    /**
     * The own commits of {@code layer}: those of its branch that are not on any of its {@code below}, nor on where
     * those were when the branch forked from them, nor on {@code base}; in the order they were made, parents first.
     */
    private Either<GitError, StackLayer.Commits> layerCommits(StackLayer layer, Revision base) {
        BranchRef branch = new BranchRef.Local(layer.branch());
        return tip(worktree, branch)
                .flatMap(tip -> Either.<GitError, List<Sha>>narrow(forkPoints(layer.below(), branch))
                        .flatMap(forks -> Either.narrow(GitProcess.read(
                                worktree,
                                GitCommand.reading(List.of(
                                                "log",
                                                "--no-color",
                                                "--topo-order",
                                                "--reverse",
                                                "--format=%H" + GitOutput.PRETTY_FIELD_SEPARATOR + "%P"
                                                        + GitOutput.PRETTY_FIELD_SEPARATOR + "%s")
                                        .appendAll(layer.below().map(GitArgs::not))
                                        .appendAll(forks.map(fork -> GitArgs.not(new Revision.Commit(fork))))
                                        .append(GitArgs.not(base))
                                        .append(GitArgs.revision(branch))
                                        .append("--")),
                                raw -> parsedLayer(layer.branch(), tip, raw)))));
    }

    /**
     * Where each of the branches among {@code below} was when {@code branch} forked from it, as its reflog says
     * ({@code git merge-base --fork-point}): a commit of it that it no longer has, as one amended since, is still not
     * the branch's own.
     */
    private Either<GitError, List<Sha>> forkPoints(List<Revision> below, BranchRef branch) {
        return Either.sequenceRight(below.filter(BranchRef.class::isInstance)
                        .map(ref -> GitProcess.ask(
                                        worktree,
                                        GitCommand.reading(
                                                "merge-base",
                                                "--fork-point",
                                                GitArgs.revision(ref),
                                                GitArgs.revision(branch)))
                                .mapLeft(GitFailure::error)
                                .flatMap(answer -> answer.fold(
                                        () -> Either.right(Option.<Sha>none()),
                                        found -> Either.<GitError, Sha>narrow(GitOutput.sha(found.stdout()))
                                                .map(Option::of)))))
                .map(found -> List.ofAll(found).flatMap(fork -> fork));
    }

    @Override
    public Either<GitError, Blame> blame(BlameAt at, BlameOptions options) {
        return CliBlame.blame(worktree, at, options);
    }

    @Override
    public Either<GitError, Boolean> everHad(BranchName branch, Revision revision) {
        BranchRef local = new BranchRef.Local(branch);
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("rev-list", "--walk-reflogs", GitArgs.revision(local), "--"),
                        raw -> Either.sequenceRight(GitOutput.nonBlankLines(raw).map(GitOutput::sha))
                                .map(List::ofAll))
                .flatMap(seen -> count(
                        worktree,
                        List.of("--max-count=1", GitArgs.revision(revision), GitArgs.not(local))
                                .appendAll(seen.map(sha -> GitArgs.not(new Revision.Commit(sha))))))
                .map(missing -> missing == 0);
    }

    /**
     * The entries of HEAD's reflog, read with {@code git log --walk-reflogs}. With {@code --date=unix}, {@code %gd}
     * is the time of the entry, {@code HEAD@{1795463129}}, so the place of an entry is counted, from {@code skip};
     * {@code --skip} counts the entries git shows, so the pages join up. git leaves out an entry whose commit is
     * gone without saying so, and the places after it are one lower than git's own then, which only a prune that
     * ignores the reflogs leads to (ADR 186).
     */
    @Override
    public Either<GitError, Reflog> reflog(int skip, int max) {
        Either<GitError, List<ReflogEntry>> read = GitProcess.read(
                worktree,
                GitCommand.reading(
                        "log",
                        "--walk-reflogs",
                        "--no-color",
                        "--no-show-signature",
                        "-z",
                        "--date=unix",
                        "--skip=" + skip,
                        "--max-count=" + max,
                        "--format=" + REFLOG_FORMAT,
                        "HEAD",
                        "--"),
                raw -> parseReflog(raw, skip));
        return skip > 0
                ? read.map(Reflog.Entries::new)
                : read.fold(
                        error -> Either.<GitError, Option<Sha>>narrow(
                                        resolve(worktree, "HEAD").mapLeft(GitFailure::error))
                                .flatMap(head ->
                                        head.isEmpty() ? Either.right(Reflog.Empty.NO_COMMITS) : Either.left(error)),
                        entries -> entries.isEmpty()
                                ? reflogsOff(worktree).map(off -> off ? Reflog.Empty.OFF : new Reflog.Entries(entries))
                                : Either.right(new Reflog.Entries(entries)));
    }

    /**
     * Two {@code git rev-list --ignore-missing <commits> --not … --branches --tags --remotes} at the same time, one
     * with {@code HEAD} among what it leaves out: those the first lists that the second does not, HEAD reaches. git
     * resolves HEAD as it runs, so it is where HEAD is then; {@code --ignore-missing} makes a HEAD without a commit
     * reach nothing. Each walks from the commits only as far as the first commit a ref reaches (ADR 188).
     */
    @Override
    public Either<GitError, Kept> lost(Seq<Sha> commits) {
        if (commits.isEmpty()) {
            return Either.right(Kept.BY_REFS);
        }
        Set<Sha> asked = HashSet.ofAll(commits);
        return Parallel.both(() -> unreached(asked, List.empty()), () -> unreached(asked, List.of("HEAD")))
                .mapLeft(CliHistory::lookFailed)
                .flatMap(both ->
                        both._1().flatMap(byRefs -> both._2().map(byAll -> new Kept(byAll, byRefs.removeAll(byAll)))));
    }

    /**
     * The error of a look for lost commits that {@code thrown} ended: an interrupted wait, or a bug.
     */
    static GitError lookFailed(Throwable thrown) {
        return thrown instanceof InterruptedException
                ? new GitError.Interrupted("rev-list")
                : new GitError.Broke("rev-list", thrown);
    }

    /**
     * Those of {@code commits} no branch, tag, remote or one of {@code also} reaches.
     */
    private Either<GitError, Set<Sha>> unreached(Set<Sha> commits, List<String> also) {
        return GitProcess.read(
                worktree,
                lostCommand(commits, also),
                raw -> Either.sequenceRight(GitOutput.nonBlankLines(raw).map(GitOutput::sha))
                        .map(listed -> HashSet.ofAll(listed).filter(commits::contains)));
    }

    private static GitCommand lostCommand(Set<Sha> commits, List<String> also) {
        return GitCommand.reading(List.of("rev-list", "--ignore-missing")
                .appendAll(commits.toList().map(sha -> GitArgs.revision(new Revision.Commit(sha))))
                .append("--not")
                .appendAll(also)
                .appendAll(List.of("--branches", "--tags", "--remotes", "--")));
    }

    /**
     * Whether git keeps no reflogs in the repository of {@code worktree}: {@code core.logAllRefUpdates} says
     * false, or is not set in a bare repository.
     */
    private static Either<GitError, Boolean> reflogsOff(Path worktree) {
        return GitProcess.ask(worktree, GitCommand.reading("config", "--get", "core.logAllRefUpdates"))
                .mapLeft(GitFailure::error)
                .flatMap(answer -> answer.fold(
                        () -> GitProcess.read(
                                worktree,
                                GitCommand.reading("rev-parse", "--is-bare-repository"),
                                raw -> Either.right(raw.strip().equals("true"))),
                        set -> Either.right(FALSE.contains(set.stdout().strip().toLowerCase(Locale.ROOT)))));
    }

    /**
     * Parses {@code git log --walk-reflogs -z} output in the format of {@link #REFLOG_FORMAT}, the first entry
     * being at {@code first}.
     */
    static Either<UnexpectedOutput, List<ReflogEntry>> parseReflog(String raw, int first) {
        return Either.sequenceRight(GitOutput.records(raw)
                        .zipWithIndex()
                        .map(record -> GitOutput.fields(record._1, 3)
                                .flatMap(fields -> entryTime(fields.get(0))
                                        .flatMap(time -> commit(fields.get(2))
                                                .map(commit -> new ReflogEntry(
                                                        first + record._2,
                                                        time,
                                                        fields.get(1),
                                                        ReflogSubjects.action(fields.get(1)),
                                                        commit))))))
                .map(Seq::toList);
    }

    /**
     * The time in a reflog selector that {@code --date=unix} gives, {@code HEAD@{1795463129}}.
     */
    private static Either<UnexpectedOutput, Instant> entryTime(String selector) {
        int open = selector.lastIndexOf("@{");
        return open >= 0 && selector.endsWith("}")
                ? GitOutput.epochSeconds(selector.substring(open + 2, selector.length() - 1))
                        .mapLeft(_ -> new UnexpectedOutput(selector))
                : Either.left(new UnexpectedOutput(selector));
    }

    /**
     * The commits of {@code branch}, at {@code tip}, in {@code raw}, a line per commit: its sha, its parents and its
     * subject. One with more than one parent is a merge.
     */
    private static Either<UnexpectedOutput, StackLayer.Commits> parsedLayer(BranchName branch, Sha tip, String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 3)
                                .flatMap(fields -> GitOutput.sha(fields.get(0))
                                        .map(sha -> Tuple.of(
                                                sha,
                                                fields.get(1).isBlank()
                                                        ? 0
                                                        : fields.get(1).split(" ").length,
                                                fields.get(2))))))
                .map(List::ofAll)
                .map(read -> new StackLayer.Commits(
                        branch,
                        tip,
                        read.filter(commit -> commit._2() < 2).map(commit -> commit._1()),
                        read.filter(commit -> commit._2() >= 2)
                                .map(commit -> new StackLayer.Merge(commit._1(), commit._3()))));
    }

    /**
     * What {@code git log --boundary} listed.
     *
     * @param commits  the commits it walked, newest first
     * @param boundary the commits it stopped at
     */
    record Walk(List<Commit> commits, List<Commit> boundary) {}
}
