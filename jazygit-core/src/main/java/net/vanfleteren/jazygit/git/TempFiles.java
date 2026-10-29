package net.vanfleteren.jazygit.git;

import io.vavr.CheckedConsumer;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.GitError.TempFileFailed;

/**
 * Temporary files that only live for the duration of one action.
 */
final class TempFiles {

    private TempFiles() {}

    /**
     * Creates a temporary file, fills it with {@code prepare}, applies {@code action} to it and deletes
     * it again.
     *
     * @param failed what the action's failure is when the file cannot be created or filled
     */
    static <L, T> Either<L, T> using(
            String prefix,
            CheckedConsumer<Path> prepare,
            Function<? super TempFileFailed, ? extends L> failed,
            Function<Path, ? extends Either<? extends L, ? extends T>> action) {
        return Try.of(() -> Files.createTempFile(prefix, ".tmp"))
                .toEither()
                .<L>mapLeft(e -> failed.apply(new TempFileFailed(e)))
                .flatMap(file -> deleting(
                        file,
                        Try.run(() -> prepare.accept(file))
                                .toEither()
                                .<L>mapLeft(e -> failed.apply(new TempFileFailed(e)))
                                .flatMap(prepared -> Either.narrow(action.apply(file)))));
    }

    /**
     * {@code outcome}, once {@code file} is deleted. A leftover temporary file is harmless, so failing to
     * delete it is not a failure.
     */
    private static <X> X deleting(Path file, X outcome) {
        var _ = Try.run(() -> Files.deleteIfExists(file));
        return outcome;
    }
}
