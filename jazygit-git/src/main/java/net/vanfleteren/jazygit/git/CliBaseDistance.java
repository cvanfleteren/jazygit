package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.model.BranchRef.HEADS;

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
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * How far each local branch is from its base (ADR 46): the commits it has that the base does not, and those the base
 * has that it does not; the base as ADR 20 has it, {@link BranchRef#bases}.
 */
final class CliBaseDistance {

    private CliBaseDistance() {}

    /**
     * The counts of each local branch that has a base, by its name, in one {@code for-each-ref} that counts against
     * every base at once (ADR 49). One base git cannot find fails that
     * {@code for-each-ref} as a whole: then each base is counted in one of its own, so that only the branches of that
     * base are left out.
     *
     * @param tips the commit of each local branch and of each branch on {@code origin}, as the branches read last have
     *             them
     * @param parents the branch each branch was made from, of those that have one recorded
     * @param main    the main branch, the base of a branch without a parent
     */
    static Map<BranchName, BaseDistance> read(
            Path worktree, Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main) {
        List<Tuple2<BranchName, Base>> bases =
                bases(tips.mapKeys(GitArgs::revision).mapValues(Sha::value), parents, main);
        Map<BranchName, Base> branches = HashMap.ofEntries(bases);
        List<String> commits = bases.map(branch -> branch._2.commit()).distinct();
        return distances(worktree, commits, branches)
                .getOrElse(() -> commits.size() < 2
                        ? HashMap.empty()
                        : commits.map(commit -> distances(worktree, List.of(commit), branches)
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
     * How far each of {@code branches} whose base is at one of {@code commits} is from it, in one {@code for-each-ref}
     * with an {@code %(ahead-behind:)} per commit; the branches by their name, with the base they are compared with by
     * its name.
     */
    private static Either<ProcessError, Map<BranchName, BaseDistance>> distances(
            Path worktree, List<String> commits, Map<BranchName, Base> branches) {
        return commits.isEmpty()
                ? Either.right(HashMap.empty())
                : GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                "for-each-ref",
                                "--format="
                                        + List.of("%(refname)", "%(objectname)")
                                                .appendAll(commits.map(commit -> "%(ahead-behind:" + commit + ")"))
                                                .mkString(GitOutput.REF_FIELD_SEPARATOR),
                                HEADS),
                        raw -> parseCounts(raw, commits, branches));
    }

    /**
     * How far each of {@code branches} is from its base, from {@code raw}, a line per local branch: its full name, the
     * commit it is at, then for each of {@code commits} the commits it has ahead of it and behind it, separated by a
     * space. Each branch takes the column of the commit its base is at; those not among {@code branches}, or whose base
     * is at none of {@code commits}, are left out.
     */
    static Either<UnexpectedOutput, Map<BranchName, BaseDistance>> parseCounts(
            String raw, List<String> commits, Map<BranchName, Base> branches) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw)
                        .map(line -> GitOutput.fields(line, 2 + commits.size())
                                .flatMap(fields -> GitOutput.after(fields.get(0), HEADS)
                                        .map(BranchName::new)
                                        .toEither(() -> new UnexpectedOutput(line))
                                        .flatMap(name -> branches.get(name)
                                                .filter(base -> commits.contains(base.commit()))
                                                .fold(
                                                        () -> Either
                                                                .<UnexpectedOutput,
                                                                        Option<Tuple2<BranchName, BaseDistance>>>
                                                                        right(Option.none()),
                                                        base -> distance(
                                                                        line,
                                                                        fields.get(1),
                                                                        fields.get(2 + commits.indexOf(base.commit())),
                                                                        base.ref())
                                                                .map(distance ->
                                                                        Option.of(Tuple.of(name, distance))))))))
                .map(found -> HashMap.ofEntries(found.flatMap(entry -> entry)));
    }

    /**
     * How far a branch at {@code tip} is from {@code base}, from {@code counts}: the commits ahead, a space, then
     * those behind.
     */
    private static Either<UnexpectedOutput, BaseDistance> distance(
            String line, String tip, String counts, BranchRef base) {
        List<String> aheadBehind = List.of(counts.split(" "));
        return aheadBehind.size() != 2
                ? Either.left(new UnexpectedOutput(line))
                : GitOutput.sha(tip)
                        .flatMap(sha -> GitOutput.number(aheadBehind.get(0))
                                .flatMap(ahead -> GitOutput.number(aheadBehind.get(1))
                                        .map(behind ->
                                                new BaseDistance(base, sha, ahead.intValue(), behind.intValue()))));
    }

    /**
     * The base of a branch, and the commit it is at.
     */
    record Base(BranchRef ref, String commit) {}
}
