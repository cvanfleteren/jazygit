package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import org.junit.jupiter.api.Test;

/**
 * The app runs without the features {@code --without} names, as if they were deleted: a feature that decides whether
 * it applies to a repository, by its id.
 */
class FeaturesWithoutTest {

    private static final Feature ALWAYS = Feature.of();
    private static final Feature FORGE = deciding("forge");
    private static final Feature AGENT = deciding("agent");
    private static final List<Feature> FEATURES = List.of(ALWAYS, FORGE, AGENT);

    private static Feature deciding(String id) {
        return Feature.of().applying(new FeatureId(id), model -> {
            throw new AssertionError("not asked");
        });
    }

    @Test
    void theIdsAreThoseOfTheFeaturesThatDecideWhetherTheyApply() {
        assertEquals(LinkedHashSet.of("forge", "agent"), Features.ids(FEATURES));
    }

    @Test
    void withoutNoneEveryFeatureRuns() {
        assertEquals(FEATURES, Features.without(FEATURES, HashSet.empty()));
    }

    @Test
    void aFeatureNamedIsLeftOut() {
        assertEquals(List.of(ALWAYS, AGENT), Features.without(FEATURES, HashSet.of("forge")));
    }

    @Test
    void severalCanBeLeftOut() {
        assertEquals(List.of(ALWAYS), Features.without(FEATURES, HashSet.of("forge", "agent")));
    }

    @Test
    void withoutThePropertyEveryFeatureRuns() {
        assertEquals(FEATURES, Features.running(FEATURES, Option.none()));
    }

    @Test
    void thePropertyNamesTheFeaturesLeftOut() {
        assertEquals(List.of(ALWAYS), Features.running(FEATURES, Option.of("forge,agent")));
    }

    @Test
    void thePropertyIgnoresSpacesAndEmptyNames() {
        assertEquals(List.of(ALWAYS, AGENT), Features.running(FEATURES, Option.of(" forge , ,")));
    }

    @Test
    void anEmptyPropertyLeavesNothingOut() {
        assertEquals(FEATURES, Features.running(FEATURES, Option.of("")));
    }

    @Test
    void theNamesAreReadInTheirOrder() {
        assertEquals(LinkedHashSet.of("forge", "agent"), Features.named(Option.of(" forge ,agent,,forge")));
    }
}
