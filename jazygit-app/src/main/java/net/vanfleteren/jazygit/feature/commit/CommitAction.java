package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * What a commit operation adds to the app: its keys, and its popups. Each operation declares its own, in its
 * subpackage; {@link CommitFeature} lists them.
 *
 * @param keys   adds the operation's keys to a keymap
 * @param popups the operation's popups, sending what the user does to the dispatch they are given
 */
public record CommitAction(UnaryOperator<Keymap> keys, Function<Consumer<Msg>, List<PopupSlot>> popups) {}
