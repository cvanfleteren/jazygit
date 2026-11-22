package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.repo.PullRequestHome;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Finds out whether Forgejo applies to the repository (ADR 171, 174): it does when the remote its pull requests are
 * opened against, its home (ADR 177), is on a host that answers as Forgejo does. It runs in the background, as it
 * asks the host.
 */
public record DetectForgejo() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        Map<RemoteName, String> urls = env.git().remotes().urls().getOrElse(HashMap::empty);
        boolean found = PullRequestHome.of(Option.none(), urls.keySet())
                .flatMap(urls::get)
                .flatMap(forgejo::repository)
                .exists(forgejo::isForgejo);
        return new FeatureMsg.Applies(ForgejoFeature.ID, found);
    }

    @Override
    public Msg failed(Failure failure) {
        return new FeatureMsg.Applies(ForgejoFeature.ID, false);
    }
}
