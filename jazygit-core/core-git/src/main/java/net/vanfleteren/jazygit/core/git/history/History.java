package net.vanfleteren.jazygit.core.git.history;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
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
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.StackLayer;

/**
 * The history, as git log shows it (ADR 69).
 */
public interface History {

    /**
     * How many commits a log or a search reads at most.
     */
    int MAX_COMMITS = 3000;

    /**
     * How many entries of the reflog {@link #reflog} reads at a time (ADR 186).
     */
    int REFLOG_PAGE = 200;

    /**
     * The log of HEAD, newest first, from {@code head}, the commit the status read last says it is at (ADR 49).
     */
    Either<GitError, List<Commit>> commits(Sha head);

    /**
     * The commits of the log of HEAD that {@code query} finds, newest first, as many as {@link #commits} reads at
     * most; none in a repository without commits.
     */
    Either<GitError, List<Commit>> search(CommitQuery query);

    /**
     * The commits from {@code tip} that are not on {@code base}, which is at {@code baseTip}: at most as many as
     * {@link #commits} reads, laid out as a log; and the commit they forked from. The tips are those the status and
     * the branches read last have, not looked up again (ADR 49).
     */
    Either<GitError, Ahead> ahead(Sha tip, BranchRef base, Sha baseTip);

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
    Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max);

    /**
     * The commits {@code tip} has and none of {@code others} has, at most {@code max}, newest first: those of a branch
     * not pushed yet, when {@code others} are the tips of its upstream and the main branch.
     */
    Either<GitError, Set<Sha>> onlyOn(Sha tip, List<Sha> others, int max);

    /**
     * The commits of the local branch {@code branch} from {@code oldest} up to its tip, and how many of them are
     * on {@code upstream}, the remote branch it follows, already.
     *
     * @param oldest the full object id of a commit of {@code branch} that has a parent
     */
    Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream);

    /**
     * The own commits of each of {@code layers}, from the bottom of a stack up: those of its branch that are not on
     * any of the {@code below} of the layer, nor on where those were when the branch forked from them, nor on
     * {@code base}, which the stack is rebased onto; parents first, the merges apart, with where the branch is now
     * (ADR 74).
     */
    Either<GitError, List<StackLayer.Commits>> stackCommits(List<StackLayer> layers, Revision base);

    /**
     * Whether the local branch {@code branch} has, or ever had, as its reflog says, all of {@code revision}: what
     * {@code git push --force-if-includes} asks before it pushes over the branch on the remote. A branch rewritten
     * here had what it was pushed as; one behind what someone else pushed never had that (ADR 74).
     */
    Either<GitError, Boolean> everHad(BranchName branch, Revision revision);

    /**
     * The entries of the reflog of HEAD of this worktree, newest first, after the newest {@code skip}: at most
     * {@code max}, each with its whole commit, read in one process (ADR 186). An entry whose commit is gone is left
     * out. Read from the top, a reflog without entries says why: HEAD has no commit yet, or git keeps no reflogs.
     */
    Either<GitError, Reflog> reflog(int skip, int max);

    /**
     * What keeps those of {@code commits} no branch, tag or remote-tracking branch reaches: only the reflog, until its
     * entries expire, or HEAD, where it is as this runs, until it moves away (ADR 186, 188). Two processes at the same
     * time for all of them, none for none.
     */
    Either<GitError, Kept> lost(Seq<Sha> commits);

    /**
     * Who last changed each line of the file {@code at} names, blamed with {@code options} (ADR 147).
     */
    Either<GitError, Blame> blame(BlameAt at, BlameOptions options);
}
