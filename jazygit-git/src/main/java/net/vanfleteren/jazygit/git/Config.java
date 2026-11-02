package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration of the repository itself, as git config --local reads and writes it (ADR 69).
 */
public interface Config {

    /**
     * The value of {@code key} in the configuration of the repository, if it is set there.
     */
    Either<ProcessError, Option<String>> local(String key);

    /**
     * Sets {@code key} to {@code value} in the configuration of the repository, or removes it there for none.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocal(String key, Option<String> value);
}
