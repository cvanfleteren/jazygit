package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Finds out whether Forgejo applies to the repository (ADR 171, 174): it does with a remote on a host that answers as
 * Forgejo does. It runs in the background, as it asks the hosts.
 */
public record DetectForgejo() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        boolean found = env.git()
                .remotes()
                .urls()
                .getOrElse(HashMap::empty)
                .values()
                .flatMap(forgejo::repository)
                .exists(forgejo::isForgejo);
        return new FeatureMsg.Applies(ForgejoFeature.ID, found);
    }

    @Override
    public Msg failed(Failure failure) {
        return new FeatureMsg.Applies(ForgejoFeature.ID, false);
    }
}
