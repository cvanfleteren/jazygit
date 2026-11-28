package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.repo.BranchRef.HEADS;
import static net.vanfleteren.jazygit.core.repo.BranchRef.REMOTES;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.BaseCandidate;
import net.vanfleteren.jazygit.core.git.process.GitArgs;
import net.vanfleteren.jazygit.core.git.process.GitCommand;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.core.git.process.GitOutput;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The branches a branch could be built on (ADR 143): every local branch and branch on the base remote counted against
 * its tip in one {@code for-each-ref}, as the distances to the bases are (ADR 46).
 */
final class CliBaseCandidates {

    private CliBaseCandidates() {}

    /**
     * The branches {@code branch}, at {@code tip}, could be built on: the tip is compared by its commit, not its name,
     * as a name can hold a {@code )}, which ends the {@code %(ahead-behind:)}.
     */
    static Either<GitError, List<BaseCandidate>> read(
            Path worktree, BranchName branch, Sha tip, Option<RemoteName> base) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(
                        "for-each-ref",
                        "--format=%(refname)" + GitOutput.REF_FIELD_SEPARATOR + "%(ahead-behind:" + tip.value() + ")",
                        HEADS,
                        prefix(base)),
                raw -> parse(raw, branch, base));
    }

    /**
     * The candidates in {@code raw}, a line per ref: its full name, then the commits it has that the tip does not and
     * those the tip has that it does not, separated by a space. Left out: {@code branch} itself, on either side; a
     * branch of which either ref has the tip, which is the branch or built on it, also when its other ref is an older
     * one that does not; and the {@code HEAD} of the base remote. Of a branch that is both local and on {@code base},
     * the one on {@code base} is counted.
     */
    static Either<UnexpectedOutput, List<BaseCandidate>> parse(String raw, BranchName branch, Option<RemoteName> base) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(line -> line(line, base)))
                .map(found -> found.flatMap(candidate -> candidate)
                        .filter(candidate -> !candidate.name().equals(branch))
                        .groupBy(BaseCandidate::name)
                        .values()
                        .filter(same -> same.forAll(candidate -> candidate.ahead() > 0))
                        .map(same -> same.find(candidate -> candidate.counted() instanceof BranchRef.Remote)
                                .getOrElse(same::head))
                        .toList());
    }

    private static Either<UnexpectedOutput, Option<BaseCandidate>> line(String line, Option<RemoteName> base) {
        return GitOutput.fields(line, 2).flatMap(fields -> {
            Option<BranchRef> ref = GitOutput.after(fields.get(0), HEADS)
                    .<BranchRef>map(name -> new BranchRef.Local(new BranchName(name)))
                    .orElse(() -> base.flatMap(remote -> GitOutput.after(fields.get(0), prefix(remote))
                            .filter(name -> !name.equals("HEAD"))
                            .map(name -> new BranchRef.Remote(new RemoteBranch(remote, new BranchName(name))))));
            List<String> counts = List.of(fields.get(1).split(" "));
            if (ref.isEmpty()) {
                return Either.right(Option.none());
            }
            return counts.size() != 2
                    ? Either.left(new UnexpectedOutput(line))
                    : GitOutput.number(counts.get(0))
                            // The ref's own commits first, then those of the tip it does not have.
                            .flatMap(behind -> GitOutput.number(counts.get(1))
                                    .map(ahead -> Option.of(new BaseCandidate(
                                            name(ref.get()), ref.get(), ahead.intValue(), behind.intValue()))));
        });
    }

    private static BranchName name(BranchRef ref) {
        return switch (ref) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(RemoteBranch remote) -> remote.branch();
        };
    }

    /**
     * The refs of the branches on {@code remote}, as a prefix; of none when there is no base remote, a prefix no ref
     * has.
     */
    private static String prefix(Option<RemoteName> remote) {
        return remote.map(CliBaseCandidates::prefix).getOrElse(REMOTES + "/");
    }

    private static String prefix(RemoteName remote) {
        return REMOTES + GitArgs.remote(remote) + "/";
    }

    /**
     * The branch git was given to make a branch from, {@code start} as its reflog spells it: {@code main},
     * {@code origin/main}, {@code refs/heads/main} or {@code refs/remotes/origin/main}. Of one on {@code base}, the
     * branch's name there. None for {@code HEAD}; a commit comes out as a name no branch has.
     */
    static Option<BranchName> startBranch(String start, Option<RemoteName> base) {
        String name = GitOutput.after(start, HEADS)
                .orElse(() -> base.flatMap(remote -> GitOutput.after(start, prefix(remote))))
                .orElse(() -> base.flatMap(remote -> GitOutput.after(start, GitArgs.remote(remote) + "/")))
                .getOrElse(start);
        return Option.of(name).filter(text -> !text.equals("HEAD")).flatMap(BranchName::parse);
    }
}
