package net.vanfleteren.jazygit.git.process;

import io.vavr.CheckedConsumer;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.git.process.GitError.TempFileFailed;

/**
 * Temporary files that only live for the duration of one action.
 */
public final class TempFiles {

    private TempFiles() {}

    /**
     * Creates a temporary file, fills it with {@code prepare}, applies {@code action} to it and deletes
     * it again.
     *
     * @param failed what the action's failure is when the file cannot be created or filled
     */
    public static <L, T> Either<L, T> using(
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
     * Creates a temporary directory, applies {@code action} to it and deletes it again, with all that the action
     * put in it, as when git writes files there itself.
     *
     * @param failed what the action's failure is when the directory cannot be created
     */
    public static <L, T> Either<L, T> usingDirectory(
            String prefix,
            Function<? super TempFileFailed, ? extends L> failed,
            Function<Path, ? extends Either<? extends L, ? extends T>> action) {
        return Try.of(() -> Files.createTempDirectory(prefix))
                .toEither()
                .<L>mapLeft(e -> failed.apply(new TempFileFailed(e)))
                .flatMap(directory -> deletingAll(directory, Either.narrow(action.apply(directory))));
    }

    /**
     * {@code outcome}, once {@code file} is deleted. A leftover temporary file is harmless, so failing to
     * delete it is not a failure.
     */
    private static <X> X deleting(Path file, X outcome) {
        Try.run(() -> Files.deleteIfExists(file));
        return outcome;
    }

    /**
     * {@code outcome}, once {@code directory} and all in it are deleted, deepest first. As with a file, failing to
     * delete it is not a failure.
     */
    private static <X> X deletingAll(Path directory, X outcome) {
        Try.run(() -> {
            try (Stream<Path> inside = Files.walk(directory)) {
                inside.sorted(Comparator.reverseOrder()).forEach(path -> Try.run(() -> Files.deleteIfExists(path)));
            }
        });
        return outcome;
    }
}
