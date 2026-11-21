package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Reads how the builds of {@code tips} went, from the Forgejo repository of each one's remote (ADR 174), in the
 * background. A tip whose remote is not on Forgejo, or that could not be read, is left out, and the log says why.
 */
public record ReadBuilds(List<Tip> tips) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        Map<RemoteName, String> urls = env.git().remotes().urls().getOrElse(HashMap::empty);
        Map<Sha, CommitStatus.State> states = HashMap.ofEntries(tips.flatMap(tip -> urls.get(tip.remote())
                .flatMap(forgejo::repository)
                .filter(forgejo::isForgejo)
                .flatMap(repository -> status(forgejo, repository, tip))
                .map(state -> Tuple.of(tip.sha(), state))));
        return new BuildsMsg.Read(tips, states);
    }

    private static Option<CommitStatus.State> status(Forgejo forgejo, ForgejoRepository repository, Tip tip) {
        return forgejo.status(repository, tip.sha())
                .peekLeft(error -> LoggerFactory.getLogger(ReadBuilds.class)
                        .debug(
                                "The builds of {} in {}/{} could not be read: {}",
                                tip.sha().shortSha(),
                                repository.owner(),
                                repository.name(),
                                error.why()))
                .map(CommitStatus::state)
                .toOption();
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsMsg.Read(tips, HashMap.empty());
    }
}
