package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * A branch on a remote, e.g. {@code feature} on {@code origin}.
 */
public record RemoteBranch(String remote, BranchName branch) {

    /**
     * The remote a branch without an upstream is pushed to.
     */
    public static final String DEFAULT_REMOTE = "origin";

    /**
     * The remote branch written as {@code <remote> <branch>}; none unless that is exactly two words.
     * Neither a remote nor a branch name can contain whitespace or start with {@code -}.
     */
    public static Option<RemoteBranch> parse(String text) {
        String[] words = text.strip().split("\\s+");
        return words.length == 2 && !words[0].startsWith("-")
                ? BranchName.parse(words[1]).map(branch -> new RemoteBranch(words[0], branch))
                : Option.none();
    }

    /**
     * The remote branch as {@link #parse} reads it.
     */
    public String text() {
        return remote + " " + branch.value();
    }

    /**
     * Its remote-tracking branch, e.g. {@code refs/remotes/origin/main}.
     */
    public BranchRef.Remote tracking() {
        return new BranchRef.Remote(this);
    }

    /**
     * The name git shows for it, e.g. {@code origin/main}.
     */
    public String shortName() {
        return remote + "/" + branch.value();
    }
}
