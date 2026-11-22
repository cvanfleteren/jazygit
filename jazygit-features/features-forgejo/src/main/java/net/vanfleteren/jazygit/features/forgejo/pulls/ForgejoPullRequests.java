package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * Forgejo's part of the model for its pull requests (ADR 157, 176): the open ones of the home (ADR 177), by the
 * branch of a remote they merge, and the reads of a pull request given out.
 *
 * @param open    the open pull requests, by the branch they merge; none until they are first read
 * @param failure why they could not be read the last time, if they could not
 * @param reads   the last read of a pull request given out, which a dialog waits for (ADR 34)
 */
public record ForgejoPullRequests(
        Option<Map<RemoteBranch, ForgejoPullRequest>> open, Option<Failure> failure, long reads) {

    public static final ForgejoPullRequests NONE = new ForgejoPullRequests(Option.none(), Option.none(), 0);

    public static final Part<ForgejoPullRequests> PART = Part.of("forgejo.pullRequests", NONE);

    /**
     * The open pull request that merges the branch {@code branch} is pushed to (ADR 177), if one does.
     */
    public Option<ForgejoPullRequest> of(Branch branch) {
        return open.flatMap(prs -> prs.get(branch.pushTarget()));
    }

    /**
     * These once the open pull requests read are {@code read}, or reading them failed as {@code failed} says: the
     * pull requests read before stay known then.
     */
    public ForgejoPullRequests listed(Option<Map<RemoteBranch, ForgejoPullRequest>> read, Option<Failure> failed) {
        return new ForgejoPullRequests(read.orElse(open), failed, reads);
    }

    /**
     * These with the next read of a pull request given out.
     */
    public ForgejoPullRequests givingOutRead() {
        return new ForgejoPullRequests(open, failure, reads + 1);
    }
}
