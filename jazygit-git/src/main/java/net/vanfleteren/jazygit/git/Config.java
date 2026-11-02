package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration of the repository itself, as git config --local reads and writes it (ADR 69), and what holds
 * for it from the user's and the system's (ADR 70).
 */
public interface Config {

    /**
     * The value of {@code key} in the configuration of the repository, if it is set there.
     */
    Either<ProcessError, Option<String>> local(String key);

    /**
     * Every value of {@code key}, from the repository's configuration, the user's and the system's, and the files
     * they include, in the order git reads them: the last is the one that holds.
     */
    Either<ProcessError, List<ConfigValue>> all(String key);

    /**
     * Sets {@code key} to {@code value} in the configuration of the repository, or removes it there for none.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocal(String key, Option<String> value);
}
