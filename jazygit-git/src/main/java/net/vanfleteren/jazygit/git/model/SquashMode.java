package net.vanfleteren.jazygit.git.model;

/**
 * How a commit is folded into its parent, as the command of its line in the todo list of an interactive
 * rebase.
 */
public enum SquashMode {
    /**
     * The changes of both, with both messages ({@code squash}).
     */
    SQUASH("squash"),
    /**
     * The changes of both, with the message of the parent ({@code fixup}).
     */
    FIXUP("fixup"),
    /**
     * The changes of both, with the message of the commit; the author stays the parent's ({@code fixup -C}).
     */
    FIXUP_KEEP_MESSAGE("fixup -C");

    private final String todoCommand;

    SquashMode(String todoCommand) {
        this.todoCommand = todoCommand;
    }

    public String todoCommand() {
        return todoCommand;
    }
}
