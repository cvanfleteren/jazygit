package net.vanfleteren.jazygit.kernel.ui.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.ZoneOffset;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * What a source puts above what another shows (ADR 187): its lines join the lead of the body, and past them it is the
 * body.
 */
class HeadedTest {

    private static final Heading HEADING = new Heading(List.of(
            List.of(Heading.Part.styled("HEAD@{1}", Theme.Classes.SHA), Heading.Part.of(" · 2h ago")), List.empty()));
    private static final DrawContext CONTEXT =
            new DrawContext(Theme.SpanStyles.NONE, ZoneOffset.UTC, Option.none(), 80);
    private static final ContentArea AREA = ContentArea.of(new AreaId("area"), new PaneId("commits"));

    @Test
    void theHeadingIsDrawnAboveTheBody() {
        assertEquals(
                List.of("HEAD@{1} · 2h ago", "", "commit a", "+x"),
                texts(new Headed(HEADING, new Plain(1)).draw(CONTEXT)));
    }

    @Test
    void theLeadOfTheBodyTakesTheHeadingIn() {
        Drawn drawn = new Headed(HEADING, new Plain(1)).draw(CONTEXT);

        assertEquals(new Anchor.Lead(), drawn.stops().head().mark().anchor());
        assertEquals(0, drawn.stops().head().row());
        assertEquals(3, drawn.stops().head().mark().lines());
        assertEquals(List.of(0, 3), drawn.stops().map(Drawn.Stop::row));
    }

    @Test
    void aBodyWithoutALeadGetsTheHeadingAsItsLead() {
        Drawn drawn = new Headed(HEADING, new Plain(0)).draw(CONTEXT);

        assertEquals(List.of(0, 2), drawn.stops().map(Drawn.Stop::row));
        assertEquals(2, drawn.stops().head().mark().lines());
    }

    @Test
    void aClickPastTheHeadingIsOnTheBodyAndOneOnItIsNothing() {
        Headed headed = new Headed(HEADING, new Plain(1));

        assertEquals(Option.none(), headed.clicked(80, 1, 0));
        assertEquals(Option.of(new Clicked(1, 4)), headed.clicked(80, 3, 4));
    }

    @Test
    void aShownHasTheHeadingAboveItsFirstAreaOnly() {
        Shown.Area area = new Shown.Area(AREA, "t", new Other(), new Plain(1));
        Shown.Message message = new Shown.Message("t", "x", Tone.DIM);

        assertEquals(
                new Shown.Stacked(List.of(area.headed(HEADING), area)),
                new Shown.Stacked(List.of(area, area)).headed(HEADING));
        assertEquals(new Headed(HEADING, new Plain(1)), ((Shown.Area) area.headed(HEADING)).body());
        assertEquals(message, message.headed(HEADING));
    }

    private static List<String> texts(Drawn drawn) {
        return drawn.lines().map(Line::rawContent);
    }

    /**
     * A commit: {@code lead} lines before its one stop, a changed line, as a commit's header is its lead.
     */
    private record Plain(int lead) implements Body {

        @Override
        public Drawn draw(DrawContext context) {
            List<Line> header = List.fill(lead, () -> Line.from("commit a"));
            Drawn change = Drawn.plain(List.of(Line.from("+x")));
            Drawn stopped = new Drawn(
                    change.rows(),
                    List.of(new Drawn.Stop(new Drawn.Mark(new Anchor.File("a"), 1, 2, Option.none()), 0)),
                    change.places(),
                    change.widest(),
                    change.halves(),
                    change.kept(),
                    change.divider(),
                    change.note());
            return stopped.after(header).withLead();
        }

        @Override
        public Option<Msg> clicked(int width, int line, int column) {
            return Option.of(new Clicked(line, column));
        }
    }

    private record Clicked(int line, int column) implements Msg {
        @Override
        public net.vanfleteren.jazygit.kernel.state.Update.Next apply(
                net.vanfleteren.jazygit.kernel.state.Model model) {
            return net.vanfleteren.jazygit.kernel.state.Update.Next.of(model);
        }
    }

    private record Other() implements ScrolledContent.Other {}
}
