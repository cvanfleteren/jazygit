package net.vanfleteren.jazygit.state.view;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Which job of a build needs which, as the files of its workflows say (ADR 131).
 */
class WorkflowTest {

    // jazygit's own: publish.yml calls build.yml, whose acceptance tests run in shards.
    private static final String PUBLISH = """
            name: Publish
            on: { push: { branches: [main] } }
            jobs:
              verify:
                uses: ./.github/workflows/build.yml
              build:
                runs-on: ubuntu-latest
                steps: [{ run: make }]
              snapshot:
                needs: [verify, build]
                runs-on: ubuntu-latest
              release:
                needs: [verify, build]
                uses: other/repo/.github/workflows/release.yml@v1
            """;
    private static final String BUILD = """
            on: workflow_call
            jobs:
              changes:
                runs-on: ubuntu-latest
              verify:
                needs: changes
              package:
                needs: "changes"
              acceptance:
                needs: package
                name: acceptance (${{ matrix.shard }}/6)
                strategy: { matrix: { shard: [1, 2, 3, 4, 5, 6] } }
              build-ok:
                needs: [changes, verify, package, acceptance]
            """;

    private static final List<String> JOBS = List.of(
            "verify / changes",
            "build",
            "verify / package",
            "verify / verify",
            "verify / acceptance (1/6)",
            "verify / acceptance (2/6)",
            "verify / build-ok",
            "snapshot");

    private static Map<String, Set<String>> needs(List<String> jobs) {
        Workflow build = Workflow.parse(BUILD).get();
        return Workflow.parse(PUBLISH)
                .get()
                .needs(jobs, path -> Option.when(path.equals(".github/workflows/build.yml"), build));
    }

    @Test
    void theJobsAreReadWithTheirNameWhatTheyNeedAndWhatTheyCall() {
        Workflow publish = Workflow.parse(PUBLISH).get();

        assertEquals(
                List.of("verify", "build", "snapshot", "release"),
                publish.jobs().map(Workflow.Job::key));
        assertEquals(List.of("verify", "build"), publish.jobs().get(2).needs());
        assertEquals(List.of(".github/workflows/build.yml"), publish.called());
    }

    @Test
    void aJobOfACalledWorkflowIsNamedAfterTheJobThatCallsItAndAShardMatchesItsName() {
        Map<String, Set<String>> needs = needs(JOBS);

        assertEquals(
                HashSet.of("verify / changes"), needs.get("verify / package").get());
        assertEquals(
                HashSet.of("verify / changes"), needs.get("verify / verify").get());
        assertEquals(
                HashSet.of("verify / package"),
                needs.get("verify / acceptance (2/6)").get());
    }

    @Test
    void aJobNeedsOnlyWhatItWaitsForDirectly() {
        assertEquals(
                HashSet.of("verify / verify", "verify / acceptance (1/6)", "verify / acceptance (2/6)"),
                needs(JOBS).get("verify / build-ok").get());
    }

    @Test
    void aJobThatNeedsACallingJobWaitsForTheLastJobsOfTheWorkflowItCalls() {
        assertEquals(
                HashSet.of("verify / build-ok", "build"),
                needs(JOBS).get("snapshot").get());
    }

    @Test
    void aJobThatNeedsNoneIsLeftOut() {
        Map<String, Set<String>> needs = needs(JOBS);

        assertFalse(needs.containsKey("build"));
        assertFalse(needs.containsKey("verify / changes"));
    }

    @Test
    void aJobNotInTheWorkflowIsLeftOut() {
        assertFalse(needs(JOBS.append("lint")).containsKey("lint"));
    }

    @Test
    void theJobsOfAWorkflowThatCannotBeReadAreLeftOut() {
        Map<String, Set<String>> needs = Workflow.parse(PUBLISH).get().needs(JOBS, path -> Option.none());

        assertEquals(HashMap.empty(), needs.filterKeys(job -> job.startsWith("verify /")));
        assertEquals(HashSet.of("build"), needs.get("snapshot").get());
    }

    @Test
    void aJobWithoutANameMatchesTheValuesOfItsMatrix() {
        Workflow matrix = Workflow.parse("""
                        jobs:
                          lint: {}
                          test:
                            needs: lint
                            strategy: { matrix: { os: [linux, mac] } }
                        """).get();

        assertEquals(
                HashSet.of("lint"),
                matrix.needs(List.of("lint", "test (linux)", "test (mac)"), path -> Option.none())
                        .get("test (mac)")
                        .get());
    }

    @Test
    void textWithoutJobsIsNoWorkflow() {
        assertEquals(Option.none(), Workflow.parse("name: nothing\n"));
        assertEquals(Option.none(), Workflow.parse("jobs: [\n"));
    }
}
