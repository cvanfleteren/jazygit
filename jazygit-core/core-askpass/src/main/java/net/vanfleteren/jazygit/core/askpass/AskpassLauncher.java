package net.vanfleteren.jazygit.core.askpass;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * The program ssh runs as its {@code SSH_ASKPASS}, which takes one program and no arguments (ADR 150): the native
 * binary itself, which runs as the helper when {@code JAZYGIT_ASKPASS} is set; with {@code java -jar}, a launcher that
 * runs the same {@code java} on the same class path with {@link AskpassHelper}, {@code askpass.sh}, or
 * {@code askpass.cmd} on Windows.
 */
final class AskpassLauncher {

    private AskpassLauncher() {}

    /**
     * The helper of this jazygit, with a launcher written into {@code directory} where it needs one.
     */
    static Try<Path> of(Path directory) {
        return Option.of(System.getProperty("org.graalvm.nativeimage.imagecode"))
                .fold(
                        () -> written(
                                directory,
                                Path.of(System.getProperty("java.home"), "bin", "java"),
                                System.getProperty("java.class.path"),
                                System.getProperty("os.name", "").startsWith("Windows")),
                        image -> Option.ofOptional(
                                        ProcessHandle.current().info().command())
                                .map(Path::of)
                                .toTry());
    }

    /**
     * A launcher in {@code directory} that runs {@code java} on {@code classPath} with {@link AskpassHelper}: a batch
     * file on {@code windows}, else a shell script only its owner can run.
     */
    static Try<Path> written(Path directory, Path java, String classPath, boolean windows) {
        return Try.of(() -> windows
                ? Files.writeString(
                        directory.resolve("askpass.cmd"),
                        "@\"" + java + "\" -cp \"" + classPath + "\" " + AskpassHelper.class.getName() + " %*\r\n",
                        StandardCharsets.UTF_8)
                : executable(Files.writeString(
                        directory.resolve("askpass.sh"),
                        "#!/bin/sh\nexec " + quoted(java.toString()) + " -cp " + quoted(classPath) + " "
                                + AskpassHelper.class.getName() + " \"$@\"\n",
                        StandardCharsets.UTF_8)));
    }

    private static Path executable(Path script) throws java.io.IOException {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                ? Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"))
                : script;
    }

    /**
     * {@code word} in single quotes, as the shell takes it as it is.
     */
    static String quoted(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }
}
