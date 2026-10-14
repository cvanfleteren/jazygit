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
     * The remote git names for this repository itself.
     */
    private static final String LOCAL_REMOTE = ".";

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
     * Whether it is a branch of this repository rather than of a remote, as for a branch created with
     * {@code --track} from a local one: its remote is {@code .}.
     */
    public boolean local() {
        return remote.equals(LOCAL_REMOTE);
    }

    /**
     * The name git shows for it, e.g. {@code origin/main}; just the branch for one of this repository, whose
     * remote is {@code .}.
     */
    public String shortName() {
        return local() ? branch : remote + "/" + branch;
    }
}
