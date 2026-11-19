package net.vanfleteren.jazygit.app.askpass;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.askpass.Asking;
import net.vanfleteren.jazygit.askpass.SecretBuffer;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.popup.SshPromptMsg;

/**
 * Asks the user what ssh asks in the popup of an {@link SshPromptDialog} (ADR 150). It holds what is typed, outside
 * the model, which only learns that the popup opens and closes. While the popup is open, a paste comes as keys, so that
 * no event holds what is pasted as a {@code String}.
 */
public final class PopupAsking implements Asking {

    private final Consumer<Runnable> onRenderThread;
    private final Consumer<Msg> dispatch;
    private final Consumer<Boolean> pasteAsKeys;
    // The question the popup asks, if it is open; only touched on the render thread.
    private Option<Pending> pending = Option.none();
    // The questions withdrawn before they reached the render thread, as their operation ended in between; only touched
    // on the render thread.
    private Set<Long> withdrawnEarly = HashSet.empty();

    /**
     * @param onRenderThread runs what it is given on the render thread, between two frames
     * @param dispatch       hands a message to the program, on the render thread
     * @param pasteAsKeys    turns bracketed paste off (true) or on again (false), on the render thread
     */
    public PopupAsking(Consumer<Runnable> onRenderThread, Consumer<Msg> dispatch, Consumer<Boolean> pasteAsKeys) {
        this.onRenderThread = onRenderThread;
        this.dispatch = dispatch;
        this.pasteAsKeys = pasteAsKeys;
    }

    @Override
    public CompletableFuture<Option<SecretBuffer>> ask(long request, String prompt) {
        CompletableFuture<Option<SecretBuffer>> answer = new CompletableFuture<>();
        onRenderThread.accept(() -> {
            if (withdrawnEarly.contains(request)) {
                withdrawnEarly = withdrawnEarly.remove(request);
                answer.complete(Option.none());
                return;
            }
            // One question at a time: one still open is withdrawn.
            pending.forEach(open -> close(open, false));
            pending = Option.of(new Pending(request, new SecretBuffer(), answer));
            pasteAsKeys.accept(true);
            dispatch.accept(new SshPromptMsg.Asked(request, prompt));
        });
        return answer;
    }

    /**
     * Closes the popup of {@code request}; one that has not opened yet, as the question is still on its way to the
     * render thread, does not open once it arrives.
     */
    @Override
    public void withdraw(long request) {
        onRenderThread.accept(() -> {
            Option<Pending> open = pending.filter(asked -> asked.request() == request);
            if (open.isDefined()) {
                close(open.get(), false);
            } else {
                withdrawnEarly = withdrawnEarly.add(request);
            }
        });
    }

    /**
     * Withdraws the question that is open, if any, as the app stops; called on the render thread.
     */
    public void withdrawAll() {
        pending.forEach(open -> close(open, false));
    }

    /**
     * How many characters were typed, for the dots; called on the render thread.
     */
    int typed() {
        return pending.map(open -> open.typed().typed()).getOrElse(0);
    }

    /**
     * Adds the character {@code codePoint} to what is typed; called on the render thread.
     */
    void type(int codePoint) {
        pending.forEach(open -> open.typed().type(codePoint));
    }

    /**
     * Takes the last character typed away; called on the render thread.
     */
    void erase() {
        pending.forEach(open -> open.typed().erase());
    }

    /**
     * Answers with what was typed; called on the render thread.
     */
    void confirm() {
        pending.forEach(open -> close(open, true));
    }

    /**
     * Answers nothing, which cancels; called on the render thread.
     */
    void cancel() {
        pending.forEach(open -> close(open, false));
    }

    /**
     * Closes the popup of {@code open}, answering with what was typed if {@code answered}, and else with nothing, what
     * was typed wiped.
     */
    private void close(Pending open, boolean answered) {
        pending = Option.none();
        pasteAsKeys.accept(false);
        dispatch.accept(new SshPromptMsg.Closed(open.request()));
        if (!answered) {
            open.typed().clear();
        }
        open.answer().complete(Option.when(answered, open::typed));
    }

    /**
     * A question the popup asks.
     *
     * @param request the question, as the server numbers it
     * @param typed   what the user typed so far
     * @param answer  what the server waits for
     */
    private record Pending(long request, SecretBuffer typed, CompletableFuture<Option<SecretBuffer>> answer) {}
}
