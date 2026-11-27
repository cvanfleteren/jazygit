package net.vanfleteren.jazygit.kernel.state;

/**
 * Which {@link KernelEvent}s an update told the features of, for the tests of a feature that raises an event the
 * feature answering it is not there for: a reaction, after those of the model, answers each event with a {@link Heard}
 * command, which the test finds among the commands.
 */
public final class HeardEvents {

    private HeardEvents() {}

    /**
     * {@code model}, with each event it is told of answered by a {@link Heard} command too.
     */
    public static Model hearing(Model model) {
        return model.withReactions(new Reactions(
                model.reactions().all().append((heard, event) -> Update.Next.of(heard, new Heard(event)))));
    }

    /**
     * A command that only says that {@code event} happened; it is never run.
     */
    public record Heard(KernelEvent event) implements Cmd {

        @Override
        public Msg run(Env env) {
            throw new UnsupportedOperationException("Heard only records an event: " + event);
        }

        @Override
        public Msg failed(Failure failure) {
            throw new UnsupportedOperationException("Heard only records an event: " + event);
        }
    }
}
