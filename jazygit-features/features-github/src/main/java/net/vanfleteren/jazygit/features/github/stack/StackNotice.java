package net.vanfleteren.jazygit.features.github.stack;

import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * What the user is told about how an operation on a stack went, when that is no failure but still needs their
 * attention: the notices of the stack, which say themselves as a phrase (ADR 169).
 */
public sealed interface StackNotice extends Notice.Phrased {

    /**
     * The stack of pull request {@code pullRequest}, linked to be rebased, was not listed by GitHub after so long
     * that it is no longer waited for: it was not rebased (ADR 74).
     */
    record NotListed(PrNumber pullRequest) implements StackNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.notice.stackNotListed", String.valueOf(pullRequest.value()));
        }
    }

    /**
     * Stack {@code stack} was ready to be rebased while another dialog was open, which it does not take the place of:
     * it was not rebased (ADR 74).
     */
    record NotAsked(StackId stack) implements StackNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.notice.stackNotAsked", String.valueOf(stack.value()));
        }
    }

    /**
     * The branches of stack {@code stack} were checked out (ADR 99): {@code made} were made, {@code fastForwarded}
     * fast-forwarded, and {@code kept} kept as they were, though not where their branch on the remote is.
     */
    record CheckedOut(StackId stack, int made, int fastForwarded, int kept) implements StackNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "github.notice.stackCheckedOut",
                    Phrase.verbatim(String.valueOf(stack.value())),
                    Phrase.count(made),
                    Phrase.count(fastForwarded),
                    Phrase.count(kept));
        }
    }
}
