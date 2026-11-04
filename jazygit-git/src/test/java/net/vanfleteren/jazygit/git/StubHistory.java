package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;

/**
 * {@link History} for tests: no commits anywhere. Fakes override what they need.
 */
public interface StubHistory extends History {

    @Override
    default Either<ProcessError, List<Commit>> commits(Sha head) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, List<Commit>> search(CommitQuery query, Option<BranchRef> notOn) {
        return Either.right(List.empty());
    }

    @Override
    default Either<ProcessError, Ahead> ahead(Sha tip, BranchRef base, Sha baseTip) {
        return Either.right(new Ahead(tip, base, List.empty(), false, Option.none()));
    }

    @Override
    default Either<GitError, List<Commit>> log(BranchRef branch) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other) {
        return Either.right(new Divergence(new Divergence.Side(List.empty(), 0), new Divergence.Side(List.empty(), 0)));
    }

    @Override
    default Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
        return Either.right(Option.none());
    }

    @Override
    default Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
        return Either.right(0);
    }

    @Override
    default Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream) {
        return Either.right(new CommitRange(1, 0, 0, Option.none()));
    }

    @Override
    default Either<GitError, List<StackLayer.Commits>> stackCommits(List<StackLayer> layers, Revision base) {
        return Either.right(layers.map(
                layer -> new StackLayer.Commits(layer.branch(), new Sha("0".repeat(40)), List.empty(), List.empty())));
    }

    @Override
    default Either<ProcessError, Boolean> everHad(BranchName branch, Revision revision) {
        return Either.right(true);
    }
}
