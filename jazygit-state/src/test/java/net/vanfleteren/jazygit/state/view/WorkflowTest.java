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
    void aJobThatNeedsNoneNeedsAnEmptySet() {
        Map<String, Set<String>> needs = needs(JOBS);

        assertEquals(Option.of(HashSet.empty()), needs.get("build"));
        assertEquals(Option.of(HashSet.empty()), needs.get("verify / changes"));
    }

    @Test
    void aJobNeededThatDidNotRunIsNeededByItsKey() {
        // The acceptance tests were skipped: build-ok waited for them, but when they were done is not known.
        Map<String, Set<String>> needs = needs(JOBS.filter(job -> !job.startsWith("verify / acceptance")));

        assertEquals(
                HashSet.of("verify / verify", "verify / package", "verify/acceptance"),
                needs.get("verify / build-ok").get());
    }

    @Test
    void aJobNotInTheWorkflowIsLeftOut() {
        assertFalse(needs(JOBS.append("lint")).containsKey("lint"));
    }

    @Test
    void theJobsOfAWorkflowThatCannotBeReadAreLeftOut() {
        Map<String, Set<String>> needs = Workflow.parse(PUBLISH).get().needs(JOBS, path -> Option.none());

        assertEquals(HashMap.empty(), needs.filterKeys(job -> job.startsWith("verify /")));
        // snapshot needs verify too, whose jobs are not known: by its key, as no job that ran is it.
        assertEquals(HashSet.of("build", "verify"), needs.get("snapshot").get());
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

    // From the review of #496: a job named only by an expression matches every name.
    private static final String BY_EXPRESSION = """
            jobs:
              build: {}
              test:
                name: ${{ matrix.os }}
                needs: build
                strategy: { matrix: { os: [Linux, Windows] } }
              deploy:
                needs: test
            """;

    @Test
    void aJobNamedOnlyByAnExpressionTakesTheNamesNoOtherJobIsWrittenAs() {
        Map<String, Set<String>> needs = Workflow.parse(BY_EXPRESSION)
                .get()
                .needs(List.of("build", "Linux", "Windows", "deploy"), path -> Option.none());

        // build and deploy are written as they run; Linux and Windows fit test alone. Not: deploy off build.
        assertEquals(
                HashMap.of(
                        "build", HashSet.<String>empty(),
                        "Linux", HashSet.of("build"),
                        "Windows", HashSet.of("build"),
                        "deploy", HashSet.of("Linux", "Windows")),
                needs);
    }

    @Test
    void aNameTwoJobsFitIsLeftOut() {
        Map<String, Set<String>> needs =
                Workflow.parse("""
                        jobs:
                          one:
                            name: ${{ matrix.os }}
                            strategy: { matrix: { os: [Linux] } }
                          two:
                            name: ${{ matrix.arch }}
                            needs: one
                            strategy: { matrix: { arch: [arm] } }
                        """).get().needs(List.of("Linux", "arm"), path -> Option.none());

        assertEquals(HashMap.empty(), needs);
    }

    @Test
    void aJobThatNeedsAJobNamedOnlyByAnExpressionNeedsItsRunsAlone() {
        Map<String, Set<String>> needs =
                Workflow.parse("""
                        jobs:
                          test:
                            name: ${{ matrix.os }}
                            strategy: { matrix: { os: [Linux, Windows] } }
                          deploy-site:
                            name: Deploy the site
                            needs: test
                        """).get().needs(List.of("Linux", "Windows", "Deploy the site"), path -> Option.none());

        // Only test matches Linux and Windows, as Deploy the site is matched by its name: deploy waits for both.
        assertEquals(
                HashMap.of(
                        "Linux", HashSet.<String>empty(),
                        "Windows", HashSet.<String>empty(),
                        "Deploy the site", HashSet.of("Linux", "Windows")),
                needs);
    }

    @Test
    void aMatrixRunGoesToTheJobWithTheMostOfItsNameWrittenOut() {
        // From the review of #496: build (17) fits both build and the job named only by an expression.
        Map<String, Set<String>> needs = Workflow.parse("""
                        jobs:
                          build:
                            strategy: { matrix: { java: [17, 21] } }
                          test:
                            name: ${{ matrix.os }}
                            needs: build
                            strategy: { matrix: { os: [Linux] } }
                          deploy:
                            needs: test
                        """)
                .get()
                .needs(List.of("build (17)", "build (21)", "Linux", "deploy"), path -> Option.none());

        assertEquals(
                HashMap.of(
                        "build (17)", HashSet.<String>empty(),
                        "build (21)", HashSet.<String>empty(),
                        "Linux", HashSet.of("build (17)", "build (21)"),
                        "deploy", HashSet.of("Linux")),
                needs);
    }

    @Test
    void aNameWithAnExpressionInItGoesToItsJobOverOneNamedOnlyByAnExpression() {
        Map<String, Set<String>> needs =
                Workflow.parse("""
                        jobs:
                          build:
                            name: Build ${{ matrix.java }}
                            strategy: { matrix: { java: [17] } }
                          test:
                            name: ${{ matrix.os }}
                            needs: build
                            strategy: { matrix: { os: [Linux] } }
                        """).get().needs(List.of("Build 17", "Linux"), path -> Option.none());

        assertEquals(HashMap.of("Build 17", HashSet.<String>empty(), "Linux", HashSet.of("Build 17")), needs);
    }
}
