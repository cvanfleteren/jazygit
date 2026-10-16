package net.vanfleteren.jazygit.state;

import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError;

/**
 * Where the app opens another repository in place of the one it shows, as if it had been started there. The app
 * does that, as it owns what runs for a repository; a {@link Cmd} only asks.
 */
public interface Repositories {

    /**
     * Opens nothing: every directory is no repository.
     */
    Repositories NONE = directory -> Either.left(new GitError.NotARepository(directory));

    /**
     * Opens the repository containing {@code directory} in place of the one shown, and gives the root of its
     * working tree. When there is none, the app stays as it is.
     */
    Either<GitError, Path> switchTo(Path directory);
}
