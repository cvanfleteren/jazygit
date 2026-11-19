package net.vanfleteren.jazygit.kernel.state;

/**
 * What happened in the kernel that a part may answer (ADR 158). An event is added when the first part needs it.
 */
public sealed interface KernelEvent {

    /**
     * The log of HEAD was read again, as HEAD moved: what is read from the history of HEAD changed with it.
     */
    record LogRead() implements KernelEvent {}
}
