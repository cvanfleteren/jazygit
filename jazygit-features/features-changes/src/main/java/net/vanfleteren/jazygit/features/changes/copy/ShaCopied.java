package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * The terminal was asked to copy the full sha of a commit to the clipboard.
 */
public record ShaCopied(Sha sha) implements Notice.Phrased {

    @Override
    public Phrase phrase() {
        return Phrase.of("changes.notice.shaCopied", sha.value());
    }
}
