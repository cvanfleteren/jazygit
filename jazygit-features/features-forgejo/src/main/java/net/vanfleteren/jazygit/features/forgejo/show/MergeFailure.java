package net.vanfleteren.jazygit.features.forgejo.show;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why Forgejo did not merge a pull request, saying what to do about it (ADR 180).
 *
 * @param error      what Forgejo answered
 * @param host       the host of the home, which fj keeps the token under
 * @param repository the home's repository
 */
public record MergeFailure(ForgejoError error, String host, RepoSlug repository) implements Failure.Phrased {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_ALLOWED = 405;
    private static final int CONFLICT = 409;
    // What Forgejo says, with a 409, when the head moved on from the commit the merge was asked for.
    private static final String MOVED = "head out of date";

    /**
     * None: merging runs no command.
     */
    @Override
    public List<ExecutedCommand> commands() {
        return List.empty();
    }

    /**
     * Whether Forgejo refused it as its head moved on since it was read: what is merged then was not seen.
     */
    public boolean moved() {
        return error instanceof ForgejoError.Refused refused
                && refused.status() == CONFLICT
                && refused.stated().exists(message -> message.contains(MOVED));
    }

    @Override
    public Phrase phrase() {
        return switch (error) {
            case ForgejoError.Refused refused
            when refused.status() == UNAUTHORIZED -> Phrase.of("forgejo.error.merge.token", host);
            case ForgejoError.Refused refused
            when refused.status() == FORBIDDEN ->
                Phrase.of("forgejo.error.merge.forbidden", repository.value(), refused.message());
            case ForgejoError.Refused refused
            when refused.status() == NOT_ALLOWED -> Phrase.of("forgejo.error.merge.notAllowed", refused.message());
            case ForgejoError.Refused _ when moved() -> Phrase.of("forgejo.error.merge.moved");
            // A merge that conflicts is answered with what failed, as Go writes it, and no message.
            case ForgejoError.Refused refused
            when refused.status() == CONFLICT && refused.stated().isEmpty() ->
                Phrase.of("forgejo.error.merge.conflicts");
            case ForgejoError.Refused refused ->
                Phrase.of("forgejo.error.merge.refused", String.valueOf(refused.status()), refused.message());
            case ForgejoError.Unreachable(String why) -> Phrase.of("forgejo.error.unreachable", why);
            case ForgejoError.Unreadable(String why) -> Phrase.of("forgejo.error.unreadable", why);
        };
    }
}
