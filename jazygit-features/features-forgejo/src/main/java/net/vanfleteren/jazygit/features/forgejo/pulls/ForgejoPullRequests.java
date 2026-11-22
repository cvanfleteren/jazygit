package net.vanfleteren.jazygit.features.forgejo.pulls;

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
 * branch of a remote they merge, and the reads of a pull request given out.
 *
 * @param home    the home they were read of
 * @param open    the open pull requests, by the branch they merge; none until they are first read
 * @param failure why they could not be read the last time, if they could not
 * @param reads   the last read of a pull request given out, which a dialog waits for (ADR 34)
 */
public record ForgejoPullRequests(
        Option<RemoteName> home,
        Option<Map<RemoteBranch, ForgejoPullRequest>> open,
        Option<Failure> failure,
        long reads) {

    public static final ForgejoPullRequests NONE =
            new ForgejoPullRequests(Option.none(), Option.none(), Option.none(), 0);

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
     * says: the pull requests read before of the same home stay known then.
     */
    public ForgejoPullRequests listed(
            RemoteName of, Option<Map<RemoteBranch, ForgejoPullRequest>> read, Option<Failure> failed) {
        Option<Map<RemoteBranch, ForgejoPullRequest>> before = home.contains(of) ? open : Option.none();
        return new ForgejoPullRequests(Option.of(of), read.orElse(before), failed, reads);
    }

    /**
     * These with the next read of a pull request given out.
     */
    public ForgejoPullRequests givingOutRead() {
        return new ForgejoPullRequests(home, open, failure, reads + 1);
    }
}
