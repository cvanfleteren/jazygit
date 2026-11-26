package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.PullRequestHome;
import net.vanfleteren.jazygit.core.repo.RemoteName;

/**
 * What the kernel makes of what the forges claimed (ADR 177): the home, no forge, or a question for the user.
 */
public sealed interface ForgeDecision {

    /**
     * What {@code claims} make the home, with {@code remembered} what the user chose before.
     *
     * <ul>
     *   <li>A remembered "no forge" stands; a remembered remote that is claimed is the home; one that a forge could
     *       not tell gives no forge, without asking; one that is no longer claimed is asked again.
     *   <li>Else one remote claimed is the home; several claimed by one forge, the one its rule picks
     *       ({@link PullRequestHome}); else the user is asked, but not while a forge could not tell of a remote,
     *       which gives no forge until the next opening.
     * </ul>
     */
    static ForgeDecision of(List<ForgeClaim> claims, ForgeChoice remembered) {
        List<ForgeCandidate> candidates = claims.flatMap(ForgeClaim::candidates);
        Set<RemoteName> unsure = claims.flatMap(ForgeClaim::unsure).toSet();
        Option<ForgeCandidate> pick = pick(claims, candidates);
        return switch (remembered) {
            case ForgeChoice.NoForge() -> new NoForge();
            case ForgeChoice.Remote(RemoteName remote) ->
                candidates
                        .find(candidate -> candidate.remote().equals(remote))
                        .<ForgeDecision>map(Home::new)
                        .getOrElse(() -> unsure.contains(remote) || candidates.isEmpty()
                                ? new NoForge()
                                : new Ask(candidates, pick));
            case ForgeChoice.Unchosen() -> unchosen(claims, candidates, unsure, pick);
        };
    }

    /**
     * What {@code claims} make the home when the user chose nothing before.
     */
    private static ForgeDecision unchosen(
            List<ForgeClaim> claims,
            List<ForgeCandidate> candidates,
            Set<RemoteName> unsure,
            Option<ForgeCandidate> pick) {
        if (candidates.isEmpty()) {
            return new NoForge();
        }
        if (candidates.size() == 1) {
            return new Home(candidates.head());
        }
        boolean oneForge = candidates.map(ForgeCandidate::feature).distinct().size() == 1;
        if (oneForge && pick.isDefined()) {
            return new Home(pick.get());
        }
        return unsure.isEmpty() ? new Ask(candidates, pick) : new NoForge();
    }

    /**
     * The candidate the rule picks: the one a forge prefers, else {@code upstream}, {@code github}, {@code origin}.
     */
    private static Option<ForgeCandidate> pick(List<ForgeClaim> claims, List<ForgeCandidate> candidates) {
        Set<RemoteName> remotes = candidates.map(ForgeCandidate::remote).toSet();
        Option<RemoteName> preferred = claims.flatMap(ForgeClaim::preferred).find(remotes::contains);
        return PullRequestHome.of(preferred, remotes)
                .filter(remotes::contains)
                .flatMap(home -> candidates.find(candidate -> candidate.remote().equals(home)));
    }

    /**
     * {@code home} is the home: its forge is on, every other off.
     */
    record Home(ForgeCandidate home) implements ForgeDecision {}

    /**
     * No forge is on.
     */
    record NoForge() implements ForgeDecision {}

    /**
     * The user is asked which of {@code candidates} is the home, if any, with {@code pick} chosen to start with.
     */
    record Ask(List<ForgeCandidate> candidates, Option<ForgeCandidate> pick) implements ForgeDecision {}
}
