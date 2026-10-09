package net.vanfleteren.jazygit.state;

/**
 * IO that {@link Update} asks for. Commands are plain data; {@link Program} performs them in the
 * background and feeds the outcome back as a {@link Msg}.
 */
public sealed interface Cmd {

    record LoadStatus() implements Cmd {
    }

    record LoadBranches() implements Cmd {
    }

    record LoadCommits() implements Cmd {
    }
}
