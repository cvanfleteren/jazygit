package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * A branch on a remote, e.g. {@code feature} on {@code origin}.
 */
public record RemoteBranch(String remote, String branch) {

    /**
     * The remote a branch without an upstream is pushed to.
     */
    public static final String DEFAULT_REMOTE = "origin";

    /**
     * The remote branch written as {@code <remote> <branch>}; none unless that is exactly two words.
     * Neither a remote nor a branch name can contain whitespace.
     */
    public static Option<RemoteBranch> parse(String text) {
        String[] words = text.strip().split("\\s+");
        return words.length == 2 ? Option.of(new RemoteBranch(words[0], words[1])) : Option.none();
    }

    /**
     * The remote branch as {@link #parse} reads it.
     */
    public String text() {
        return remote + " " + branch;
    }

    /**
     * The name git shows for it, e.g. {@code origin/main}; just the branch for one of this repository, whose
     * remote is {@code .}.
     */
    public String shortName() {
        return remote.equals(".") ? branch : remote + "/" + branch;
    }
}
