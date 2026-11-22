package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * The warning in the command log that fj's token goes to the home of the pull requests unencrypted, as its remote is
 * over plain HTTP (ADR 174): no failure, as the user chose the remote.
 *
 * @param base where the home is asked, as {@code http://code.example.net}
 */
public record PlainHttpNotice(String base) implements Notice.Phrased {

    @Override
    public Phrase phrase() {
        return Phrase.of("forgejo.notice.plainHttp", base);
    }
}
