package net.vanfleteren.jazygit.git.model;

/**
 * What a reset of the checked out branch to another commit does with the changes in between and the
 * uncommitted ones, as the option of {@code git reset}.
 */
public enum ResetMode {
    /**
     * Keeps them in the working tree, unstaged ({@code --mixed}).
     */
    MIXED("--mixed"),
    /**
     * Keeps them, staged ({@code --soft}).
     */
    SOFT("--soft"),
    /**
     * Discards them; untracked files stay ({@code --hard}).
     */
    HARD("--hard");

    private final String option;

    ResetMode(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }
}
