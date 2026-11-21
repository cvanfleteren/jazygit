package net.vanfleteren.jazygit.features.github.stack;

import net.vanfleteren.jazygit.core.gh.model.PrNumber;
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
}
