package net.vanfleteren.jazygit.ui.content;

import java.util.function.Function;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;

/**
 * A scrollable area of the content panel, and the pane whose highlighted row it shows. The {@link ContentSource} that
 * shows it declares it (ADR 154).
 *
 * @param id   the id of the area, which is also the id of the element that draws it
 * @param pane the pane whose highlighted row the area shows in a model
 */
public record ContentArea(AreaId id, Function<Model, PaneId> pane) {

    /**
     * The area {@code id}, which shows what {@code pane} has highlighted, whatever the model.
     */
    public static ContentArea of(AreaId id, PaneId pane) {
        return new ContentArea(id, model -> pane);
    }

    /**
     * The pane whose highlighted row the area shows in {@code model}, which gets the focus back when Escape, or Left
     * once the area is scrolled all the way left, is pressed in it.
     */
    public PaneId pane(Model model) {
        return pane.apply(model);
    }
}
