package net.vanfleteren.jazygit.features.forgejo.show;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * What Forgejo said of a merge scheduled for when the checks succeed that differs from what was read (ADR 181): no
 * failure, as what was asked for is so.
 */
public sealed interface ScheduleNotice extends Notice.Phrased {

    /**
     * The merge of the pull request of {@code branch} was scheduled already.
     */
    record AlreadyScheduled(BranchName branch) implements ScheduleNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.notice.schedule.already", branch.value());
        }
    }

    /**
     * The merge of the pull request of {@code branch} was no longer scheduled: it was merged, or merging it failed.
     */
    record NoLongerScheduled(BranchName branch) implements ScheduleNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.notice.schedule.gone", branch.value());
        }
    }
}
