package net.vanfleteren.jazygit.core.git.history;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.git.model.BlameAt;
import net.vanfleteren.jazygit.core.git.model.BlameOptions;
import net.vanfleteren.jazygit.core.git.model.CommitQuery;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Ahead;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.CommitRange;
import net.vanfleteren.jazygit.core.repo.Divergence;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.StackLayer;

/**
 * {@link History} for tests: no commits anywhere. Fakes override what they need.
 */
public interface StubHistory extends History {

    @Override
    default Either<GitError, List<Commit>> commits(Sha head) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, List<Commit>> search(CommitQuery query) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitError, Ahead> ahead(Sha tip, BranchRef base, Sha baseTip) {
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
    default Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
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
    default Either<GitError, Boolean> everHad(BranchName branch, Revision revision) {
        return Either.right(true);
    }

    @Override
    default Either<GitError, Reflog> reflog(int skip, int max) {
        return Either.right(new Reflog.Entries(List.empty()));
    }

    @Override
    default Either<GitError, Set<Sha>> lost(Seq<Sha> commits) {
        return Either.right(HashSet.empty());
    }

    @Override
    default Either<GitError, Blame> blame(BlameAt at, BlameOptions options) {
        return Either.right(new Blame(Vector.empty()));
    }
}
