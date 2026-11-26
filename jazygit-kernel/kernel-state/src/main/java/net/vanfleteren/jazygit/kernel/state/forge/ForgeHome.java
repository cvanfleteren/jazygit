package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * Which forge a repository is on, and the remote of its pull requests, its home (ADR 177): what the forges claimed as
 * it opened, what the user chose before, and what the kernel made of it.
 *
 * @param awaiting   the forges that have not told their claims yet
 * @param claims     what the forges claimed, in the order they told it
 * @param remembered what the user chose before, once it was read
 * @param home       the home, while its forge is on
 * @param decided    whether the kernel decided, so that it does so once an opening
 */
@With
public record ForgeHome(
        Set<String> awaiting,
        List<ForgeClaim> claims,
        Option<ForgeChoice> remembered,
        Option<ForgeCandidate> home,
        boolean decided) {

    public static final ForgeHome NONE =
            new ForgeHome(HashSet.empty(), List.empty(), Option.none(), Option.none(), false);

    // Another worktree of the same repository has the same remotes and git configuration, so the same home (ADR 182).
    public static final Part<ForgeHome> PART = Part.of("kernel.forgeHome", NONE).carrying(ForgeHome::carried);

    /**
     * The remote the pull requests of the repository of {@code model} are opened against, while the forge it is on
     * is on (ADR 177).
     */
    public static Option<RemoteName> of(Model model) {
        return model.part(PART).home().map(ForgeCandidate::remote);
    }

    /**
     * These waiting for the claims of {@code feature} too.
     */
    public ForgeHome awaiting(String feature) {
        return withAwaiting(awaiting.add(feature));
    }

    /**
     * These with {@code claim} told; as they are for a forge that is not waited for.
     */
    public ForgeHome claimed(ForgeClaim claim) {
        return awaiting.contains(claim.feature())
                ? withAwaiting(awaiting.remove(claim.feature())).withClaims(claims.append(claim))
                : this;
    }

    /**
     * These with what the user chose before read: {@code chosen}.
     */
    public ForgeHome remembering(ForgeChoice chosen) {
        return withRemembered(Option.of(chosen));
    }

    /**
     * Whether everything the kernel decides from is known, and it did not decide yet.
     */
    public boolean due() {
        return !decided && remembered.isDefined() && awaiting.isEmpty() && !claims.isEmpty();
    }

    /**
     * These once the kernel decided, with {@code chosen} the home, if any.
     */
    public ForgeHome deciding(Option<ForgeCandidate> chosen) {
        return withHome(chosen).withDecided(true);
    }

    /**
     * What another worktree of the same repository opens with (ADR 182): the home, once it was decided, so that the
     * forges do not tell their claims again; nothing while it was not, or while the user put off the question, which
     * is asked again as the worktree opens, as at any opening (ADR 177).
     */
    public Option<ForgeHome> carried() {
        return Option.when(
                decided && (home.isDefined() || candidates().isEmpty()), () -> withAwaiting(HashSet.empty()));
    }

    /**
     * The remotes the home can be, as the forges claimed them.
     */
    public List<ForgeCandidate> candidates() {
        return claims.flatMap(ForgeClaim::candidates);
    }
}
