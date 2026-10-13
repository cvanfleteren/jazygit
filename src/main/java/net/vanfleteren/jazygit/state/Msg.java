package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Everything that can happen to the {@link Model}, grouped by feature. A message knows how to
 * apply itself: each feature has its own sub-interface, which delegates to the transitions of that
 * feature, so adding a feature needs no change here or in {@link Update}.
 */
public interface Msg {

    /**
     * The next model, and the IO to perform for it, given that this happened to {@code model}.
     */
    Update.Next apply(Model model);

    /**
     * Periodic prompt to check the repository for changes made outside the app.
     */
    record Tick() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.refresh(model);
        }
    }

    /**
     * The element with the id {@code id}, outside the popups, got the focus.
     */
    record Focused(String id) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withFocusedPane(Option.of(id)));
        }
    }
}
