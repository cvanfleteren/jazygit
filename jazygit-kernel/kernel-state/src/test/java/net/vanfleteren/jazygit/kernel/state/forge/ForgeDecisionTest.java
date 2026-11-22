package net.vanfleteren.jazygit.kernel.state.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import org.junit.jupiter.api.Test;

/**
 * What the kernel makes of what the forges claimed (ADR 177): it decides alone in the usual setups, and asks only
 * when guessing would be wrong.
 */
class ForgeDecisionTest {

    static ForgeClaim claim(String feature, String... remotes) {
        return new ForgeClaim(
                feature,
                feature,
                LinkedHashMap.ofEntries(
                        List.of(remotes).map(remote -> io.vavr.Tuple.of(new RemoteName(remote), url(remote)))),
                HashSet.empty(),
                Option.none());
    }

    static String url(String remote) {
        return "https://example.net/" + remote + ".git";
    }

    static ForgeCandidate candidate(String feature, String remote) {
        return new ForgeCandidate(feature, feature, new RemoteName(remote), url(remote));
    }

    private static ForgeDecision decide(List<ForgeClaim> claims) {
        return ForgeDecision.of(claims, Option.none());
    }

    private static Option<Option<RemoteName>> remembered(String remote) {
        return Option.of(Option.of(new RemoteName(remote)));
    }

    @Test
    void oneRemoteClaimedIsTheHome() {
        assertEquals(
                new ForgeDecision.Home(candidate("forgejo", "origin")),
                decide(List.of(claim("github"), claim("forgejo", "origin"))));
    }

    @Test
    void noRemoteClaimedIsNoForge() {
        assertEquals(new ForgeDecision.NoForge(), decide(List.of(claim("github"), claim("forgejo"))));
    }

    @Test
    void severalOfOneForgeArePickedByTheRuleAsAForkIs() {
        assertEquals(
                new ForgeDecision.Home(candidate("forgejo", "upstream")),
                decide(List.of(claim("forgejo", "origin", "upstream"))));
    }

    @Test
    void theRemoteAForgePrefersComesFirst() {
        ForgeClaim preferring = new ForgeClaim(
                "github",
                "github",
                claim("github", "origin", "upstream").claimed(),
                HashSet.empty(),
                Option.of(new RemoteName("origin")));

        assertEquals(new ForgeDecision.Home(candidate("github", "origin")), decide(List.of(preferring)));
    }

    @Test
    void severalOfOneForgeTheRuleCannotPickAreAsked() {
        assertEquals(
                new ForgeDecision.Ask(List.of(candidate("forgejo", "a"), candidate("forgejo", "b")), Option.none()),
                decide(List.of(claim("forgejo", "a", "b"))));
    }

    @Test
    void remotesOfTwoForgesAreAskedTheRulesPickHighlighted() {
        assertEquals(
                new ForgeDecision.Ask(
                        List.of(candidate("github", "origin"), candidate("forgejo", "codeberg")),
                        Option.of(candidate("github", "origin"))),
                decide(List.of(claim("github", "origin"), claim("forgejo", "codeberg"))));
    }

    @Test
    void aRemoteAForgeCannotTellIsNoReasonToAskOrToPickAnother() {
        ForgeClaim down = new ForgeClaim(
                "forgejo", "forgejo", LinkedHashMap.empty(), HashSet.of(new RemoteName("codeberg")), Option.none());

        // What is certain is decided.
        assertEquals(
                new ForgeDecision.Home(candidate("github", "origin")),
                decide(List.of(claim("github", "origin"), down)));
        // What would be asked is not, until the next opening.
        assertEquals(new ForgeDecision.NoForge(), decide(List.of(claim("github", "a", "b"), down)));
        // A remembered remote it cannot tell gives no forge, without asking.
        assertEquals(
                new ForgeDecision.NoForge(),
                ForgeDecision.of(List.of(claim("github", "origin"), down), remembered("codeberg")));
    }

    @Test
    void aRememberedRemoteThatIsClaimedWins() {
        assertEquals(
                new ForgeDecision.Home(candidate("forgejo", "codeberg")),
                ForgeDecision.of(
                        List.of(claim("github", "origin"), claim("forgejo", "codeberg")), remembered("codeberg")));
    }

    @Test
    void aRememberedRemoteNoLongerClaimedIsAskedAgain() {
        assertEquals(
                new ForgeDecision.Ask(List.of(candidate("github", "origin")), Option.of(candidate("github", "origin"))),
                ForgeDecision.of(List.of(claim("github", "origin")), remembered("gone")));
    }

    @Test
    void aRememberedNoForgeStands() {
        assertEquals(
                new ForgeDecision.NoForge(),
                ForgeDecision.of(List.of(claim("github", "origin")), Option.of(Option.none())));
    }
}
