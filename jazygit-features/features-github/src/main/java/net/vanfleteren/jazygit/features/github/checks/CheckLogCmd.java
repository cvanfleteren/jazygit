package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Reads the log of the check {@code name}, the job {@code run} of {@code repository}, from GitHub, or from where it was
 * kept when it was read before, to open it in an editor (ADR 160).
 *
 * @param repository the repository, as {@code owner/name}
 */
public record CheckLogCmd(RepoSlug repository, String name, CheckRun run) implements OperationCmd {

    public static final Operation DOWNLOAD_LOG =
            Operation.of("github.log.title.downloadLog", "github.error.downloadLog.failed");

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
        return GitHubServices.of(env)
                .checks()
                .jobLog(repository, run.runId(), run.runAttempt(), run.id())
                .fold(
                        failure -> failed(GitHubFailure.of(failure)),
                        log -> new ChecksMsg.LogRead(this, name, log.file().toString(), log.line(), log.commands()));
    }
}
