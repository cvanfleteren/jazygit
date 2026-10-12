package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;

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
        this.commands = commands;
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
        return new GitCommandException(getMessage(), earlier.appendAll(commands), getCause());
    }

    /**
     * Runs the steps in order and returns all command lines they executed. When a step fails, the
     * exception also carries the commands of the steps before it.
     */
    @SafeVarargs
    public static List<String> chain(Supplier<List<String>>... steps) {
        List<String> executed = List.empty();
        for (Supplier<List<String>> step : steps) {
            try {
                executed = executed.appendAll(step.get());
            } catch (GitCommandException e) {
                throw e.after(executed);
            }
        }
        return executed;
    }
}
