package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * A branch on a remote, e.g. {@code feature} on {@code origin}.
 */
public record RemoteBranch(RemoteName remote, BranchName branch) {

    /**
     * The remote a branch without an upstream is pushed to.
     */
    public static final RemoteName DEFAULT_REMOTE = new RemoteName("origin");

    /**
     * {@code branch} on the remote named {@code remote}, as spelled in the code, e.g. {@code "origin"}.
     */
    public RemoteBranch(String remote, BranchName branch) {
        this(new RemoteName(remote), branch);
    }

    /**
     * The remote branch written as {@code <remote> <branch>}; none unless that is exactly two words.
     * Neither a remote nor a branch name can contain whitespace or start with {@code -}.
     */
    public static Option<RemoteBranch> parse(String text) {
        String[] words = text.strip().split("\\s+");
        return words.length == 2
                ? RemoteName.parse(words[0])
                        .flatMap(remote -> BranchName.parse(words[1]).map(branch -> new RemoteBranch(remote, branch)))
                : Option.none();
    }

    /**
     * The remote branch as {@link #parse} reads it.
     */
    public String text() {
        return remote.value() + " " + branch.value();
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
        return remote.value() + "/" + branch.value();
    }
}
