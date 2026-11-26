package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * What the user is told of a copy to the clipboard (ADR 94): what the terminal was asked to copy, or why nothing was.
 */
public sealed interface CopyNotice extends Notice.Phrased {

    /**
     * The terminal was asked to copy {@code lines} lines of a diff to the clipboard.
     */
    record LinesCopied(int lines) implements CopyNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.copied", Phrase.count(lines));
        }
    }

    /**
     * The terminal was asked to copy the path {@code path} to the clipboard.
     */
    record PathCopied(String path) implements CopyNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.pathCopied", path);
        }
    }

    /**
     * The terminal was asked to copy the full sha of a commit to the clipboard.
     */
    record ShaCopied(Sha sha) implements CopyNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.shaCopied", sha.value());
        }
    }

    /**
     * Nothing could be copied, as the app has no terminal of its own to ask.
     */
    record NotCopied() implements CopyNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.notCopied");
        }
    }

    /**
     * Nothing was copied, as the terminal could not be written to.
     */
    record CopyFailed() implements CopyNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.copyFailed");
        }
    }
}
