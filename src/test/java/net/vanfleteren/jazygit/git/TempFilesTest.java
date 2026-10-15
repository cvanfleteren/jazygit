package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.control.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TempFilesTest {

    @Test
    void theActionSeesThePreparedFileWhichIsDeletedAfterwards() {
        AtomicReference<Path> used = new AtomicReference<>();

        Either<GitError, String> outcome =
                TempFiles.using("test", file -> Files.writeString(file, "content"), failed -> failed, file -> {
                    used.set(file);
                    return Either.right(readQuietly(file));
                });

        assertEquals(Either.right("content"), outcome);
        assertFalse(Files.exists(used.get()));
    }

    @Test
    void aFileThatCannotBePreparedIsATempFileFailure() {
        AtomicReference<Path> used = new AtomicReference<>();

        Either<GitError, String> outcome = TempFiles.using(
                "test",
                file -> {
                    used.set(file);
                    throw new IOException("disk full");
                },
                failed -> failed,
                file -> Either.right("never"));

        GitError.TempFileFailed failed = assertInstanceOf(GitError.TempFileFailed.class, outcome.getLeft());
        assertEquals("disk full", failed.cause().getMessage());
        assertFalse(Files.exists(used.get()));
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return e.toString();
        }
    }
}
