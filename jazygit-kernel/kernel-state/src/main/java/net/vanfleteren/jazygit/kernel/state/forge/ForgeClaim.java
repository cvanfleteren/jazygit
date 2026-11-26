package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.FeatureId;

/**
 * What a forge found of the remotes of a repository as it opened (ADR 177): those on it, those it could not tell, and
 * the one it was told is the home, if any. The kernel names no forge: it only reads these.
 *
 * @param feature   the id of the forge's feature, by which it is switched on (ADR 171)
 * @param forge     the forge's name, as the user is shown it, like {@code GitHub}
 * @param claimed   the remotes on the forge, each with its URL, in the order of the remotes
 * @param unsure    the remotes it could not tell, as one on a host that could not be reached
 * @param preferred the remote the forge's own setting names the home, as gh's {@code set-default}, if any
 */
public record ForgeClaim(
        FeatureId feature,
        String forge,
        Map<RemoteName, String> claimed,
        Set<RemoteName> unsure,
        Option<RemoteName> preferred) {

    /**
     * A forge that claims no remote, and can tell of each.
     */
    public static ForgeClaim none(FeatureId feature, String forge) {
        return new ForgeClaim(feature, forge, LinkedHashMap.empty(), HashSet.empty(), Option.none());
    }

    /**
     * Each remote claimed, as one the home can be.
     */
    public List<ForgeCandidate> candidates() {
        return claimed.toList().map(remote -> new ForgeCandidate(feature, forge, remote._1(), remote._2()));
    }
}
