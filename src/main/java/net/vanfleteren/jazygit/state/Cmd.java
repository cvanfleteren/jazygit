package net.vanfleteren.jazygit.state;

/**
 * IO that {@link Update} asks for. Commands are plain data; {@link Program} performs them in the
 * background and feeds the outcome back as a {@link Msg}.
 */
public sealed interface Cmd {

    /**
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {
    }

    record LoadStatus() implements Load {
    }

    record LoadBranches() implements Load {
    }

    record LoadCommits() implements Load {
    }

    record LoadBranchLog(String branch) implements Load {
    }

    record Checkout(String branch) implements Cmd {
    }
}
