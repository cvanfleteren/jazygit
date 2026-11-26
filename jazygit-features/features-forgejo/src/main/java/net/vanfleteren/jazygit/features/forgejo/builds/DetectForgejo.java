package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteUrl;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeClaim;

/**
 * Finds out which remotes of the repository are on Forgejo (ADR 174, 177), so that the kernel decides whether Forgejo
 * is its forge: those on a host that answers as Forgejo does. A host that could not be reached, as one behind a VPN
 * that is not up, cannot tell. It runs in the background, as it asks the hosts.
 */
public record DetectForgejo() implements Cmd {

    // The forge, as the user is shown it when asked which forge a repository is on (ADR 177).
    private static final String NAME = "Forgejo";

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        List<Tuple2<RemoteName, RemoteUrl>> urls = env.git()
                .remotes()
                .urls()
                .getOrElse(HashMap::empty)
                .toList()
                .sortBy(remote -> remote._1().value());
        List<Tuple2<Tuple2<RemoteName, RemoteUrl>, Option<Boolean>>> told = urls.flatMap(remote ->
                forgejo.repository(remote._2()).map(repository -> Tuple.of(remote, forgejo.tells(repository))));
        Map<RemoteName, RemoteUrl> claimed = LinkedHashMap.ofEntries(
                told.filter(remote -> remote._2().contains(true)).map(Tuple2::_1));
        return new FeatureMsg.Claimed(new ForgeClaim(
                ForgejoFeature.ID,
                NAME,
                claimed,
                told.filter(remote -> remote._2().isEmpty())
                        .map(remote -> remote._1()._1())
                        .toSet(),
                Option.none()));
    }

    @Override
    public Msg failed(Failure failure) {
        return new FeatureMsg.Claimed(ForgeClaim.none(ForgejoFeature.ID, NAME));
    }
}
