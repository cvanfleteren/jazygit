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
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The history, as git log shows it (ADR 69).
 */
public interface History {

    /**
     * How many commits a log or a search reads at most.
     */
    int MAX_COMMITS = 300;

    /**
     * The log of HEAD, newest first, from {@code head}, the commit the status read last says it is at (ADR 49).
     */
    Either<ProcessError, List<Commit>> commits(Sha head);

    /**
     * The commits of the log of HEAD that {@code query} finds, newest first, as many as {@link #commits} reads at
     * most; none in a repository without commits.
     *
     * @param notOn the branch whose commits are left out, if any
     */
    Either<ProcessError, List<Commit>> search(CommitQuery query, Option<BranchRef> notOn);

    /**
     * The commits from {@code tip} that are not on {@code base}, which is at {@code baseTip}: at most as many as
     * {@link #commits} reads, laid out as a log; and the commit they forked from. The tips are those the status and
     * the branches read last have, not looked up again (ADR 49).
     */
    Either<ProcessError, Ahead> ahead(Sha tip, BranchRef base, Sha baseTip);

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    Either<GitError, List<Commit>> log(BranchRef branch);

    /**
     * The commits only on {@code branch} and the ones only on {@code other}, as {@code git log branch...other}
     * shows them.
     */
    Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other);

    /**
     * The subject of the oldest commit on {@code branch} that is not on {@code base}; none when there is none.
     */
    Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base);

    /**
     * How many commits are on {@code branch} and not on {@code base}, counting no further than {@code max}.
     */
    Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max);

    /**
     * The commits of the local branch {@code branch} from {@code oldest} up to its tip, and how many of them are
     * on {@code upstream}, the remote branch it follows, already.
     *
     * @param oldest the full object id of a commit of {@code branch} that has a parent
     */
    Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream);
}
