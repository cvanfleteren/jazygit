package net.vanfleteren.jazygit.git;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A git command that failed. Besides git's explanation as message, it knows the command lines that were
 * executed up to and including the failing one, so they can be logged.
 */
public class GitCommandException extends IllegalStateException {

    private final List<String> commands;

    public GitCommandException(String message, List<String> commands) {
        this(message, commands, null);
    }

    public GitCommandException(String message, List<String> commands, Throwable cause) {
        super(message, cause);
        this.commands = List.copyOf(commands);
    }

    /**
     * The command lines that were executed, the failing one last.
     */
    public List<String> commands() {
        return commands;
    }

    /**
     * This failure, as part of a sequence in which the {@code earlier} commands already succeeded.
     */
    public GitCommandException after(List<String> earlier) {
        List<String> all = new ArrayList<>(earlier);
        all.addAll(commands);
        return new GitCommandException(getMessage(), all, getCause());
    }

    /**
     * Runs the steps in order and returns all command lines they executed. When a step fails, the
     * exception also carries the commands of the steps before it.
     */
    @SafeVarargs
    public static List<String> chain(Supplier<List<String>>... steps) {
        List<String> executed = new ArrayList<>();
        for (Supplier<List<String>> step : steps) {
            try {
                executed.addAll(step.get());
            } catch (GitCommandException e) {
                throw e.after(executed);
            }
        }
        return executed;
    }
}
