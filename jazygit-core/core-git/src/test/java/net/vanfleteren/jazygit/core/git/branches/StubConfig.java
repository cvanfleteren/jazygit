package net.vanfleteren.jazygit.core.git.branches;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ConfigValue;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * {@link Config} for tests: nothing is set, and setting succeeds without running anything. Fakes override what
 * they need.
 */
public interface StubConfig extends Config {

    @Override
    default Either<GitError, Option<String>> local(String key) {
        return Either.right(Option.none());
    }

    @Override
    default Either<GitError, List<ConfigValue>> all(String key) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> setLocal(String key, Option<String> value) {
        return ran();
    }
}
