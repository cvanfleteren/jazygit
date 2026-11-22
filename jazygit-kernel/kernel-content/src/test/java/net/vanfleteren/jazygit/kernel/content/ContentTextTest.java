package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

class ContentTextTest {

    private static final String VERSIONS = """
            diff --git a/pom.xml b/pom.xml
            --- a/pom.xml
            +++ b/pom.xml
            @@ -1,2 +1,2 @@
            -<boo.version>123</boo.version>
            -<bar.version>456</bar.version>
            +<boo.version>124</boo.version>
            +<bar.version>457</bar.version>
            """;

    @Test
    void theRichViewPairsTheChangedLinesAsTheLookSays() {
        assertEquals(
                List.of(
                        "1   │ - <boo.version>123</boo.version>",
                        "  1 │ + <boo.version>124</boo.version>",
                        "2   │ - <bar.version>456</bar.version>",
                        "  2 │ + <bar.version>457</bar.version>"),
                changed(true));
        assertEquals(
                List.of(
                        "1   │ - <boo.version>123</boo.version>",
                        "2   │ - <bar.version>456</bar.version>",
                        "  1 │ + <boo.version>124</boo.version>",
                        "  2 │ + <bar.version>457</bar.version>"),
                changed(false));
    }

    private static List<String> changed(boolean paired) {
        return List.ofAll(ContentText.diffText(
                                new ShownDiff(DiffText.of(VERSIONS), Option.none()),
                                new DiffLook(DiffStyle.RICH, true, true, paired),
                                Theme.SpanStyles.NONE)
                        .lines())
                .map(Line::rawContent)
                .filter(line -> line.contains("│ -") || line.contains("│ +"))
                .map(String::stripTrailing);
    }
}
