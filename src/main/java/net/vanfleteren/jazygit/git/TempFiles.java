package net.vanfleteren.jazygit.git;

import io.vavr.CheckedConsumer;
import io.vavr.control.Either;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.git.GitError.TempFileFailed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Temporary files that only live for the duration of one action.
 */
final class TempFiles {

    private TempFiles() {
    }

    /**
     * Creates a temporary file, fills it with {@code prepare}, applies {@code action} to it and deletes
     * it again.
     */
    static <T> Either<TempFileFailed, T> using(String prefix, CheckedConsumer<Path> prepare,
                                               Function<Path, T> action) {
        return Try.of(() -> Files.createTempFile(prefix, null))
                .flatMap(file -> Try.run(() -> prepare.accept(file)).map(prepared -> file).onFailure(e -> delete(file)))
                .toEither()
                .<TempFileFailed>mapLeft(e -> new TempFileFailed(String.valueOf(e.getMessage())))
                .map(file -> {
                    try {
                        return action.apply(file);
                    } finally {
                        delete(file);
                    }
                });
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover temporary file is harmless.
        }
    }
}
