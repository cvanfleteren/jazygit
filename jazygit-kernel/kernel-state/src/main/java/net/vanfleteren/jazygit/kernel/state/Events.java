package net.vanfleteren.jazygit.kernel.state;

/**
 * Telling the features what happened in the kernel (ADR 158).
 */
public final class Events {

    private Events() {}

    /**
     * {@code next}, once the features answered {@code event}: first the parts that are kept, then the reactions of
     * the model, each in order, with what they ask for coming after what {@code next} asked for.
     */
    public static Update.Next happened(Update.Next next, KernelEvent event) {
        return next.then(model -> Parts.reacting(model, event))
                .then(model -> model.reactions().to(Update.Next.of(model), event));
    }
}
