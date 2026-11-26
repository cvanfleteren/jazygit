package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.Part;
import org.junit.jupiter.api.Test;

/**
 * The model keeps each {@link Part} under its name (ADR 157), so no two parts the app declares may share one: the
 * second would read the value of the first. Every constant of the app's classes that holds a part counts, whether a
 * feature or any other module declares it.
 */
class PartNamesTest {

    private static List<Part<?>> declared() {
        return Constants.of(Part.class).map(part -> part);
    }

    @Test
    void theSwitchesOfTheFeaturesAreAPart() {
        assertTrue(declared().exists(part -> part.name().equals("kernel.features")));
    }

    @Test
    void noTwoPartsShareAName() {
        List<String> names = declared().map(Part::name);
        assertEquals(names.distinct(), names, "two parts share a name");
    }
}
