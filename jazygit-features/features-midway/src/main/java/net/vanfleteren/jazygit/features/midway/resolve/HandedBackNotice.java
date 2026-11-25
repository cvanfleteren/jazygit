package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * Where a file in conflict is once a merge tool or mergiraf handed it back (ADR 201).
 */
public sealed interface HandedBackNotice extends Notice.Phrased {

    /**
     * The file at {@code path} is still in conflict, but has no markers left: staging it marks it resolved.
     */
    record NoMarkersLeft(String path) implements HandedBackNotice {
        @Override
        public Phrase phrase() {
            return Phrase.of("midway.notice.noMarkersLeft", path);
        }
    }

    /**
     * The file at {@code path} still has {@code conflicts} conflicts, the first starting at {@code line}.
     */
    record MarkersLeft(String path, int conflicts, int line) implements HandedBackNotice {
        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "midway.notice.markersLeft", Phrase.verbatim(path), Phrase.count(conflicts), Phrase.count(line));
        }
    }
}
