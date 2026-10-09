package net.vanfleteren.jazygit.state;

import java.util.List;

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

    record LoadFileDiff(List<net.vanfleteren.jazygit.model.FileEntry> files) implements Load {

        public LoadFileDiff {
            files = List.copyOf(files);
        }
    }

    record Checkout(String branch) implements Cmd {
    }

    /**
     * Creates the branch {@code name} at {@code base} and switches to it.
     */
    record CreateBranch(String name, String base) implements Cmd {
    }

    record Stage(List<String> paths) implements Cmd {

        public Stage {
            paths = List.copyOf(paths);
        }
    }

    /**
     * Takes paths out of the index: the {@code added} ones (not in HEAD) become untracked again, the
     * {@code others} are reset to HEAD.
     */
    record Unstage(List<String> added, List<String> others) implements Cmd {

        public Unstage {
            added = List.copyOf(added);
            others = List.copyOf(others);
        }
    }
}
