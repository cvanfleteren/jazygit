package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;

/**
 * What each side of a merge did to a file that is in conflict.
 *
 * @param ours   what the current branch did
 * @param theirs what the branch being merged did
 */
public record Conflict(Side ours, Side theirs) {

    /**
     * The conflict described by the two status letters of {@code git status --porcelain=v2}
     * ({@code DD AU UD UA DU AA UU}): the first is for the current branch, the second for the other one.
     */
    public static Conflict of(char ours, char theirs) {
        return new Conflict(side(ours), side(theirs));
    }

    private static Side side(char letter) {
        return letter == 'D' ? Side.DELETED : Side.UPDATED;
    }

    /**
     * What {@code version}'s side did to the file.
     */
    public Side did(Version version) {
        return version == Version.OURS ? ours : theirs;
    }

    /**
     * The git commands keeping {@code version}'s side of the file runs, in order, each as its arguments after
     * {@code git} and before {@code -- <path>}: {@code checkout --ours|--theirs}, then {@code add}; or
     * {@code rm --quiet} when that side deleted it (ADR 198). The resolve dialog says them as they are run (ADR 200).
     */
    public List<List<String>> keeping(Version version) {
        return did(version) == Side.DELETED
                ? List.of(List.of("rm", "--quiet"))
                : List.of(List.of("checkout", version == Version.OURS ? "--ours" : "--theirs"), List.of("add"));
    }

    /**
     * What a branch did to the file.
     */
    public enum Side {
        /**
         * Changed or added.
         */
        UPDATED,
        DELETED
    }

    /**
     * Which side's version of a file in conflict is kept (ADR 198): {@code OURS} is the side of HEAD, the branch checked
     * out or, in a rebase, what it rebases onto; {@code THEIRS} is the other, the branch merged or the commit being
     * rebased, cherry-picked or reverted.
     */
    public enum Version {
        OURS,
        THEIRS
    }
}
