package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * Forgejo's part of the model for its pull requests (ADR 157, 176): the open ones of the home (ADR 177), by the
 * branch of a remote they merge, the reads of a pull request given out, and the merges that failed (ADR 180).
 *
 * @param home         the home they were read of
 * @param open         the open pull requests, by the branch they merge; none until they are first read
 * @param failure      why they could not be read the last time, if they could not
 * @param reads        the last read of a pull request given out, which a dialog waits for (ADR 34)
 * @param failedMerges the last merge that failed of each pull request, by its number, while it is open
 */
public record ForgejoPullRequests(
        Option<RemoteName> home,
        Option<Map<RemoteBranch, ForgejoPullRequest>> open,
        Option<Failure> failure,
        long reads,
        Map<Long, FailedMerge> failedMerges) {

    public static final ForgejoPullRequests NONE =
            new ForgejoPullRequests(Option.none(), Option.none(), Option.none(), 0, HashMap.empty());

    public static final Part<ForgejoPullRequests> PART = Part.of("forgejo.pullRequests", NONE);

    /**
     * The open pull request of {@code home} that merges the branch {@code branch} is pushed to (ADR 177), if one does;
     * none from a list of another home.
     */
    public Option<ForgejoPullRequest> of(Branch branch, RemoteName home) {
        return open.filter(prs -> read(home)).flatMap(prs -> prs.get(branch.pushTarget()));
    }

    /**
     * Whether the open pull requests of {@code home} were read.
     */
    public boolean read(RemoteName home) {
        return open.isDefined() && this.home.contains(home);
    }

    /**
     * These once the open pull requests of {@code of} read are {@code read}, or reading them failed as {@code failed}
     * says: the pull requests read before of the same home stay known then. A failed merge of a pull request no longer
     * open is dropped, as are all of them once the home is another.
     */
    public ForgejoPullRequests listed(
            RemoteName of, Option<Map<RemoteBranch, ForgejoPullRequest>> read, Option<Failure> failed) {
        boolean same = home.contains(of);
        Option<Map<RemoteBranch, ForgejoPullRequest>> before = same ? open : Option.none();
        Map<Long, FailedMerge> merges = !same
                ? HashMap.empty()
                : read.map(prs -> failedMerges.filterKeys(number -> prs.values().exists(pr -> pr.number() == number)))
                        .getOrElse(failedMerges);
        return new ForgejoPullRequests(Option.of(of), read.orElse(before), failed, reads, merges);
    }

    /**
     * These with {@code created}, just created in {@code of} (ADR 178), as the open pull request that merges
     * {@code head}, before the list is read again; unchanged when they are of another home, or not read yet.
     */
    public ForgejoPullRequests created(RemoteName of, RemoteBranch head, ForgejoPullRequest created) {
        return new ForgejoPullRequests(
                home,
                open.filter(prs -> read(of)).map(prs -> prs.put(head, created)).orElse(open),
                failure,
                reads,
                failedMerges);
    }

    /**
     * These with the next read of a pull request given out.
     */
    public ForgejoPullRequests givingOutRead() {
        return new ForgejoPullRequests(home, open, failure, reads + 1, failedMerges);
    }

    /**
     * These with {@code failed} as the last merge that failed of its pull request, in place of one before.
     */
    public ForgejoPullRequests mergeFailed(FailedMerge failed) {
        return new ForgejoPullRequests(home, open, failure, reads, failedMerges.put(failed.number(), failed));
    }

    /**
     * These once pull request {@code number} was merged: a merge of it that failed before is forgotten.
     */
    public ForgejoPullRequests merged(long number) {
        return new ForgejoPullRequests(home, open, failure, reads, failedMerges.remove(number));
    }
}
