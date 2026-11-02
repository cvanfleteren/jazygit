package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.StubGitProvider.ran;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Editor} for tests: no editor runs, and what is edited is saved as it is. Fakes override what they need.
 */
public interface StubEditor extends Editor {

    @Override
    default Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files) {
        return ran();
    }

    /**
     * Saves the message as it is.
     */
    @Override
    default Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(
            CommitMessage message) {
        return Either.right(Tuple.of(message, List.empty()));
    }

    /**
     * Saves the text as it is.
     */
    @Override
    default Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
        return Either.right(Tuple.of(text, List.empty()));
    }
}
