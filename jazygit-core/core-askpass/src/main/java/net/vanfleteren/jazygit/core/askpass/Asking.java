package net.vanfleteren.jazygit.core.askpass;

import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the user what ssh asks, in a popup (ADR 150). Called on the thread that handles a request on the socket; what
 * the user does comes on the render thread.
 */
public interface Asking {

    /**
     * Asks nothing: every question is refused at once.
     */
    Asking NOBODY = new Asking() {
        @Override
        public CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt) {
            return CompletableFuture.completedFuture(Option.none());
        }

        @Override
        public void withdraw(long request) {}
    };

    /**
     * Opens the popup that asks {@code prompt}, as the question {@code request}.
     *
     * @return what the user typed, once they confirmed it, which the caller wipes once it is used; none once they
     *     cancelled, or the question was withdrawn
     */
    CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt);

    /**
     * Closes the popup of the question {@code request}, if it is still open: the operation that asked it ended. What
     * was typed in it is wiped, and its answer is none.
     */
    void withdraw(long request);
}
