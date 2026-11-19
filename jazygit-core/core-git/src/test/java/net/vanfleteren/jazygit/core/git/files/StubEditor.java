package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.StubGit.ran;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * {@link Editor} for tests: no editor runs, and what is edited is saved as it is. Fakes override what they need.
 */
public interface StubEditor extends Editor {

    @Override
    default Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
        return ran();
    }

    /**
     * Saves the message as it is.
     */
    @Override
    default Either<GitFailure, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(CommitMessage message) {
        return Either.right(Tuple.of(message, List.empty()));
    }

    /**
     * Saves the text as it is.
     */
    @Override
    default Either<GitFailure, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text) {
        return Either.right(Tuple.of(text, List.empty()));
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> open(Path directory, List<String> commandLine) {
        return ran();
    }

    @Override
    default Either<GitFailure, List<ExecutedCommand>> launch(Path directory, List<String> commandLine) {
        return ran();
    }
}
