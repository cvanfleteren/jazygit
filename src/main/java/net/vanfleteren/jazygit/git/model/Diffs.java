package net.vanfleteren.jazygit.git.model;

/**
 * The diff of some files as unified diff text, split by where the changes are.
 *
 * @param staged   the changes in the index, against HEAD
 * @param unstaged the changes in the working tree, against the index; untracked files count as entirely added
 */
public record Diffs(String staged, String unstaged) {

    public boolean isEmpty() {
        return staged.isBlank() && unstaged.isBlank();
    }
}
