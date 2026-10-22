package net.vanfleteren.jazygit.state;

public sealed interface AheadMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AheadUpdate.update(model, this);
    }

    /**
     * The user switched between the whole logs and only the commits of their branches that are not on its base.
     */
    record Toggled() implements AheadMsg {}
}
