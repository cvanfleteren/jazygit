package net.vanfleteren.jazygit.git.model;

/**
 * What each side of a merge did to a file that is in conflict.
 *
 * @param ours   what the current branch did
 * @param theirs what the branch being merged did
 */
public record Conflict(Side ours, Side theirs) {

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
     * The conflict described by the two status letters of {@code git status --porcelain=v2}
     * ({@code DD AU UD UA DU AA UU}): the first is for the current branch, the second for the other one.
     */
    public static Conflict of(String xy) {
        return new Conflict(side(xy.charAt(0)), side(xy.charAt(1)));
    }

    private static Side side(char letter) {
        return letter == 'D' ? Side.DELETED : Side.UPDATED;
    }
}
