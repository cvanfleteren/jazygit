package net.vanfleteren.jazygit.features.changes.files.step;

import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * The highlight of the files pane could not step to another file, as there is none that way (ADR 117).
 */
public sealed interface FileStepNotice extends Notice.Phrased {

    /**
     * The highlight was on the last file, so there was no next file to move to.
     */
    record NoNextFile() implements FileStepNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.noNextFile");
        }
    }

    /**
     * The highlight was on the first file, so there was no previous file to move to.
     */
    record NoPreviousFile() implements FileStepNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.notice.noPreviousFile");
        }
    }
}
