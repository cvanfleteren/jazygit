package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.model.BranchRef.HEADS;
import static net.vanfleteren.jazygit.git.model.BranchRef.REMOTES;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * How many commits the base of each local branch has that the branch does not (ADR 46): the base as ADR 20 has it,
 * {@link BranchRef#bases}.
 */
final class CliBehind {

    private static final String ORIGIN = REMOTES + GitArgs.remote(RemoteBranch.DEFAULT_REMOTE) + "/";
    private static final String PARENT_PREFIX = "branch.";
    private static final String PARENT_SUFFIX = ".gh-merge-base";

    private CliBehind() {}

    /**
     * The count of each local branch that has a base, by its name: one {@code git config} for the parents, one
     * {@code for-each-ref} for the refs, then one {@code for-each-ref} per base. The branches of a base whose count
     * cannot be read are left out, not all of them.
     *
     * @param main the main branch, the base of a branch without a parent
     */
    static Either<ProcessError, Map<BranchName, Integer>> read(Path worktree, Option<BranchName> main) {
        return parents(worktree)
                .flatMap(parents -> GitProcess.read(
                                worktree,
                                GitCommand.reading(
                                        "for-each-ref",
                                        "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(objectname)",
                                        HEADS,
                                        ORIGIN),
                                CliBehind::parseRefs)
                        .map(refs -> bases(refs, parents, main)))
                .map(bases -> bases.groupBy(Tuple2::_2)
                        .map(group -> behind(
                                        worktree,
                                        group._1,
                                        group._2.map(Tuple2::_1).toSet())
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
    static List<Tuple2<BranchName, String>> bases(
            Map<String, String> refs, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        return refs.keySet()
                .toList()
                .sorted()
                .flatMap(ref -> GitOutput.after(ref, HEADS))
                .map(BranchName::new)
                .flatMap(branch -> BranchRef.bases(Option.of(branch), parents.get(branch), main)
                        .map(GitArgs::revision)
                        .find(refs::containsKey)
                        .flatMap(refs::get)
                        .map(sha -> Tuple.of(branch, sha)));
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
     * How many commits of {@code base}, a commit, each of {@code branches} does not have.
     */
    private static Either<ProcessError, Map<BranchName, Integer>> behind(
            Path worktree, String base, Set<BranchName> branches) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                "for-each-ref",
                                "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(ahead-behind:" + base + ")",
                                HEADS),
                        CliBehind::parseBehind)
                .map(counts -> counts.filterKeys(branches::contains));
    }

    /**
     * The counts in {@code raw}, a line per branch: its full name, then the commits it has ahead of the base and
     * behind it, separated by a space.
     */
    static Either<UnexpectedOutput, Map<BranchName, Integer>> parseBehind(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 2).flatMap(fields -> {
                            List<String> counts = List.of(fields.get(1).split(" "));
                            return GitOutput.after(fields.get(0), HEADS)
                                    .filter(name -> counts.size() == 2)
                                    .toEither(() -> new UnexpectedOutput(line))
                                    .flatMap(name -> GitOutput.number(counts.get(1))
                                            .map(behind -> Tuple.of(new BranchName(name), behind.intValue())));
                        })))
                .map(HashMap::ofEntries);
    }
}
