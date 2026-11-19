package net.vanfleteren.jazygit.kernel.ui.content;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * A scrollable area of the content panel, and the pane whose highlighted row it shows. The {@link ContentSource} that
 * shows it declares it (ADR 154).
 *
 * @param id     the id of the area, which is also the id of the element that draws it
 * @param pane   the pane whose highlighted row the area shows in a model
 * @param opened what is opened to show in the area in a model, which it takes the focus from its pane for once it
 *               shows it, as the builds of a branch after {@code B} (ADR 131, 155); none while nothing is, as for most
 *               areas, which take the focus only when it is moved into them
 */
public record ContentArea(AreaId id, Function<Model, PaneId> pane, Function<Model, Option<?>> opened) {

    /**
     * The area {@code id}, which shows what {@code pane} has highlighted in a model, and takes the focus only when it
     * is moved into it.
     */
    public ContentArea(AreaId id, Function<Model, PaneId> pane) {
        this(id, pane, model -> Option.none());
    }

    /**
     * The area {@code id}, which shows what {@code pane} has highlighted, whatever the model.
     */
    public static ContentArea of(AreaId id, PaneId pane) {
        return new ContentArea(id, model -> pane);
    }

    /**
     * This area, taking the focus from its pane for what {@code opened} says is opened in a model once it shows it.
     */
    public ContentArea opening(Function<Model, Option<?>> opened) {
        return new ContentArea(id, pane, opened);
    }

    /**
     * The pane whose highlighted row the area shows in {@code model}, which gets the focus back when Escape, or Left
     * once the area is scrolled all the way left, is pressed in it.
     */
    public PaneId pane(Model model) {
        return pane.apply(model);
    }

    /**
     * What is opened to show in the area in {@code model}: when it is another than before, the area takes the focus
     * from its pane once it shows it; when it is gone while the area has the focus, the pane gets it back.
     */
    public Option<?> opened(Model model) {
        return opened.apply(model);
    }
}
