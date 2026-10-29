package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.model.BranchRef.HEADS;
import static net.vanfleteren.jazygit.git.model.BranchRef.REMOTES;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.BaseDistance;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * How far each local branch is from its base (ADR 46): the commits it has that the base does not, and those the base
 * has that it does not; the base as ADR 20 has it, {@link BranchRef#bases}.
 */
final class CliBaseDistance {

    private static final String ORIGIN = REMOTES + GitArgs.remote(RemoteBranch.DEFAULT_REMOTE) + "/";
    private static final String PARENT_PREFIX = "branch.";
    private static final String PARENT_SUFFIX = ".gh-merge-base";

    private CliBaseDistance() {}

    /**
     * The counts of each local branch that has a base, by its name: one {@code git config} for the parents, one
     * {@code for-each-ref} for the refs, then one {@code for-each-ref} per base. The branches of a base whose count
     * cannot be read are left out, not all of them.
     *
     * @param main the main branch, the base of a branch without a parent
     */
    static Either<ProcessError, Map<BranchName, BaseDistance>> read(Path worktree, Option<BranchName> main) {
        return parents(worktree)
                .flatMap(parents -> GitProcess.read(
                                worktree,
                                GitCommand.reading(
                                        "for-each-ref",
                                        "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(objectname)",
                                        HEADS,
                                        ORIGIN),
                                CliBaseDistance::parseRefs)
                        .map(refs -> bases(refs, parents, main)))
                .map(bases -> bases.groupBy(branch -> branch._2.commit())
                        .map(group -> distances(worktree, group._1, HashMap.ofEntries(group._2))
                                .getOrElse(HashMap.empty()))
                        .fold(HashMap.empty(), Map::merge));
    }

    /**
     * Each local branch among {@code refs} that has a base there, with the commit that base is at: the commit, not
     * its name, is what it is compared with, as a name can hold a {@code )}, which ends the {@code %(ahead-behind:)}
     * of {@code for-each-ref}, and the ref can be gone by then.
     *
     * @param refs the commit of each ref, by its full name
     */
    static List<Tuple2<BranchName, Base>> bases(
            Map<String, String> refs, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        return refs.keySet()
                .toList()
                .sorted()
                .flatMap(ref -> GitOutput.after(ref, HEADS))
                .map(BranchName::new)
                .flatMap(branch -> BranchRef.bases(Option.of(branch), parents.get(branch), main)
                        .flatMap(base -> refs.get(GitArgs.revision(base)).map(sha -> new Base(base, sha)))
                        .headOption()
                        .map(base -> Tuple.of(branch, base)));
    }

    /**
     * The refs in {@code raw}, a line per ref: its full name, then the commit it is at.
     */
    static Either<UnexpectedOutput, Map<String, String>> parseRefs(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 2).map(fields -> Tuple.of(fields.get(0), fields.get(1)))))
                .map(HashMap::ofEntries);
    }

    /**
     * The parent of each branch that has one recorded, as {@code gh} does.
     */
    private static Either<ProcessError, Map<BranchName, BranchName>> parents(Path worktree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(
                                "config",
                                "--local",
                                "--get-regexp",
                                "^" + PARENT_PREFIX.replace(".", "\\.") + ".*" + PARENT_SUFFIX.replace(".", "\\.")
                                        + "$"))
                .mapLeft(GitFailure::error)
                .map(answer ->
                        answer.map(output -> parseParents(output.stdout())).getOrElse(HashMap.empty()));
    }

    /**
     * The parents in {@code raw}, as {@code git config --get-regexp} lists them: the key, a space and the value per
     * line. A value that is no branch name is left out.
     */
    static Map<BranchName, BranchName> parseParents(String raw) {
        return HashMap.ofEntries(GitOutput.nonBlankLines(raw).flatMap(line -> {
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            Option<BranchName> branch = Option.when(
                            key.startsWith(PARENT_PREFIX)
                                    && key.endsWith(PARENT_SUFFIX)
                                    && key.length() > PARENT_PREFIX.length() + PARENT_SUFFIX.length(),
                            () -> key.substring(PARENT_PREFIX.length(), key.length() - PARENT_SUFFIX.length()))
                    .map(BranchName::new);
            Option<BranchName> parent =
                    Option.when(space >= 0, () -> line.substring(space + 1)).flatMap(BranchName::parse);
            return branch.flatMap(name -> parent.map(value -> Tuple.of(name, value)));
        }));
    }

    /**
     * How far each of {@code branches} is from {@code base}, a commit; the branches by their name, with the base they
     * are compared with by its name.
     */
    private static Either<ProcessError, Map<BranchName, BaseDistance>> distances(
            Path worktree, String base, Map<BranchName, Base> branches) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                "for-each-ref",
                                "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(ahead-behind:" + base + ")",
                                HEADS),
                        CliBaseDistance::parseCounts)
                .map(counts -> counts.flatMap((name, count) ->
                        branches.get(name).map(of -> Tuple.of(name, new BaseDistance(of.ref(), count._1, count._2)))));
    }

    /**
     * The counts in {@code raw}, a line per branch: its full name, then the commits it has ahead of the base and
     * behind it, separated by a space; each as the commits ahead and those behind.
     */
    static Either<UnexpectedOutput, Map<BranchName, Tuple2<Integer, Integer>>> parseCounts(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 2).flatMap(fields -> {
                            List<String> counts = List.of(fields.get(1).split(" "));
                            return GitOutput.after(fields.get(0), HEADS)
                                    .filter(name -> counts.size() == 2)
                                    .toEither(() -> new UnexpectedOutput(line))
                                    .flatMap(name -> GitOutput.number(counts.get(0))
                                            .flatMap(ahead -> GitOutput.number(counts.get(1))
                                                    .map(behind -> Tuple.of(
                                                            new BranchName(name),
                                                            Tuple.of(ahead.intValue(), behind.intValue())))));
                        })))
                .map(HashMap::ofEntries);
    }

    /**
     * The base of a branch, and the commit it is at.
     */
    record Base(BranchRef ref, String commit) {}
}
