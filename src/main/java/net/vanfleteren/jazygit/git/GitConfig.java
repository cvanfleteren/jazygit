package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration of the repository itself, as {@code git config --local} reads and writes it.
 */
public interface GitConfig {

    /**
     * The value of {@code key} in the configuration of the repository, if it is set there.
     */
    Either<ProcessError, Option<String>> localConfig(String key);

    /**
     * Sets {@code key} to {@code value} in the configuration of the repository, or removes it there for none.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value);
}
