package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck.ActionsJob;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Reads the log of the check {@code name}, the job {@code job} of Forgejo Actions of the repository of {@code branch},
 * and keeps it in a file, to open it in an editor (ADR 160, 195). It is read anew each time, as a job run again keeps
 * its place in its run.
 */
public record CheckLogCmd(RemoteBranch branch, String name, ActionsJob job) implements OperationCmd {

    public static final Operation DOWNLOAD_LOG =
            Operation.of("forgejo.log.title.downloadLog", "forgejo.error.downloadLog.failed");

    // What the runner of Forgejo Actions marks a step that failed with.
    static final String FAILURE = "❌";

    // The characters an owner and a repository name on Forgejo may have, so that no file is ever outside the logs.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public Operation operation() {
        return DOWNLOAD_LOG;
    }

    @Override
    public Option<String> subject() {
        return Option.of(name);
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return LoadChecks.repository(env, forgejo, branch.remote())
                .flatMap(repository -> file(ForgejoServices.logs(env), repository)
                        .flatMap(file -> forgejo.jobLog(repository, job)
                                .<Failure>mapLeft(ForgejoFailure.Asked::new)
                                .flatMap(log -> kept(file, log))))
                .fold(this::failed, kept -> kept);
    }

    /**
     * Where the log is kept in {@code logs}: {@code <host>/<owner>/<name>/<run>-<job>.log}; none for what is no host, owner
     * or name Forgejo allows.
     */
    Either<Failure, Path> file(Path logs, ForgejoRepository repository) {
        // A host with a port, as code.example.net:3000, without the colon no file system takes.
        String host = repository.host().replace(':', '_');
        List<String> parts = List.of(host, repository.owner(), repository.name());
        return parts.forAll(part -> NAME.matcher(part).matches() && !part.equals(".") && !part.equals(".."))
                ? Either.right(logs.resolve(host)
                        .resolve(repository.owner())
                        .resolve(repository.name())
                        .resolve(job.run() + "-" + job.job() + ".log"))
                : Either.left(new ForgejoFailure.LogNotKept(repository.owner() + "/" + repository.name(), "no name"));
    }

    /**
     * {@code log} kept in {@code file}: written next to it first, then moved there, so that a log cut off halfway is
     * never taken for one kept.
     */
    private Either<Failure, Msg> kept(Path file, String log) {
        return Try.of(() -> {
                    Path directory = Files.createDirectories(file.getParent());
                    Path part =
                            Files.createTempFile(directory, file.getFileName().toString(), ".part");
                    Files.writeString(part, log, StandardCharsets.UTF_8);
                    return Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                })
                .toEither()
                .<Failure>mapLeft(
                        cause -> new ForgejoFailure.LogNotKept(file.toString(), String.valueOf(cause.getMessage())))
                .map(kept -> new ChecksMsg.LogRead(this, name, kept.toString(), line(log), List.empty()));
    }

    /**
     * The line to open the log {@code text} at, counted from 1: the first that says a step failed, else the last.
     */
    static int line(String text) {
        List<String> lines = List.ofAll(text.lines());
        int failed = lines.indexWhere(line -> line.contains(FAILURE));
        return failed >= 0 ? failed + 1 : Math.max(1, lines.size());
    }
}
