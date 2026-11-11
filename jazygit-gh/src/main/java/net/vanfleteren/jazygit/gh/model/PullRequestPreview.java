package net.vanfleteren.jazygit.gh.model;

/**
 * What a pull request says and changes, to show it.
 *
 * @param base      the branch it merges into
 * @param body      its description, as written
 * @param diff      what it changes, as a unified diff, or only its first lines when it is truncated
 * @param truncated whether lines of the diff were left out
 */
public record PullRequestPreview(
        GhBranch base,
        String body,
        String diff,
        boolean truncated,
        io.vavr.control.Option<PullRequestDetails> details,
        io.vavr.control.Option<io.vavr.collection.List<PullRequestEvent>> timeline) {

    public PullRequestPreview(GhBranch base, String body, String diff, boolean truncated) {
        this(base, body, diff, truncated, io.vavr.control.Option.none(), io.vavr.control.Option.none());
    }

    /**
     * PROTOTYPE: this preview, with what happened on it.
     */
    public PullRequestPreview withTimeline(io.vavr.control.Option<io.vavr.collection.List<PullRequestEvent>> read) {
        return new PullRequestPreview(base, body, diff, truncated, details, read);
    }

    /**
     * PROTOTYPE: this preview, with the details the overview shows.
     */
    public PullRequestPreview withDetails(io.vavr.control.Option<PullRequestDetails> read) {
        return new PullRequestPreview(base, body, diff, truncated, read, timeline);
    }
}
