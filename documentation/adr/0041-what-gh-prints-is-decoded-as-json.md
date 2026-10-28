# 41. What gh prints is decoded as JSON

Date: 2026-10-28

## Context

`CliGitHub` reads GitHub through gh, and every read hands gh a `--jq` filter that turns the answer into tab separated
lines, which Java parses by the position of each field. The class has 1126 lines:

- Every query is written twice, once in jq and once in Java, and nothing ties the two together. A field added to the
  jq on one side moves the indexes on the other; that fails as `UnexpectedOutput`, with no word of which field.
- 61 `f.get(n)` calls hang off 7 constants of how many fields a line has, and splitting the lines and undoing
  `@tsv`'s escapes is copied into 8 places.
- The jq holds logic of its own: the state of a check, the workflow runs that wait to start (ADR 35), when a pull
  request was last pushed to as the latest of three timestamps, and which comments show and in what order. It is
  only tested by running jq (`CliGitHubJqTest`, ADR 35), so the tests need jq on the machine.
- `PullRequestDetails` has 24 parameters; its parser, and its withers `withDraft` and `withBehindBase`, repeat all of
  them in order.
- `UnexpectedOutput` sometimes carries the whole output of gh, which the user is shown as it is.

No JSON library is on the classpath. The native image finds reflection metadata in GraalVM's repository.

## Decision

gh prints the JSON that GitHub answers, and Java decodes it into the model with decoders of its own. No query passes
`--jq` any more.

### The `jazygit-json` module

A Maven module of its own, `net.vanfleteren.jazygit.json`, which jazygit-core depends on. It depends on vavr, jspecify
and Jackson 3's `tools.jackson.core:jackson-core`, nothing of jazygit.

- `Json` is a sealed interface of records: `JObject` (a vavr `LinkedHashMap<String, Json>`), `JArray` (a vavr
  `List<Json>`), `JString`, `JNumber` (a `BigDecimal`), `JBool` and `JNull`. `Json.parse` builds it with Jackson's
  streaming parser; no other class uses Jackson. Not: Jackson databind's `JsonNode`, which is mutable and larger.
  Not: jakarta.json with Parsson, which needs its provider found by `ServiceLoader`. With a type of its own, a
  decoder matches it in a `switch`, and the library can be swapped behind `parse`.
- `Decoder<T>` turns a `Json` into `Either<DecodeError, T>`, stopping at the first error: one is enough to find a
  query and its decoder that went apart. Not: vavr's `Validation`, which collects them all.
- A `DecodeError` is the path to where it failed, like `data.repository.pullRequest.commits.nodes[0].commit
  .committedDate`, and what was wrong there, like `expected a string, got null`. Each field and index adds to the path.
- What decoders are made of: `string`, `bool`, `integer`, `longValue`, `instant`, `enumOf`; `field` and `at` for a
  path of fields; `nullable` and `list`; `dict` for an object whose names are data, like the hosts gh is logged in to
  or the aliases of a query; `map`, `flatMap`, `map2` to `map8` on vavr's functions; `succeed` and `fail`;
  and `byField("__typename", decoders)` for GraphQL's unions, like a check that is a `CheckRun` or a `StatusContext`.
  A record of more than eight fields is built from decoders of its parts.
- A missing field fails; `null` is only taken where `nullable` gives it as `Option.none()`. GraphQL and `gh --json`
  always answer each field asked for, so a missing one is a query and its decoder that went apart. Not: missing and
  `null` alike, which hides that.
- But for the fields asked for in a fragment on a union or an interface: a node of another type answers none of them,
  like a search hit that is not a pull request, which is `{}`. So each such fragment asks for `__typename` too, and
  its node is decoded with `byField("__typename", decoders)`, which takes a fallback for the types it does not know:
  one that leaves the node out, as `Option.none()` that the list flattens, or one that fails, as the read does now.
  Not: a decoder that recovers from any failed field or element, which would let a query and its decoder go apart
  unseen anywhere.
- The ArchUnit tests of jazygit-core and jazygit-app say that no class of theirs uses `tools.jackson`, which reaches
  them through `jazygit-json`. Not: Maven's `dependency:analyze`, which is stricter about every dependency.

### The queries

- `GhProcess.readJson(command, decoder)` runs gh, parses what it prints and decodes it. A parse error or a
  `DecodeError` is `UnexpectedOutput` with the path and what was wrong: what the user is shown says what failed, and
  no longer the whole output. `ErrorText` and the messages stay as they are. Not: a `GhError` of its own.
- Each query is a class of its own, with its GraphQL, its variables and its decoder. What queries share is a class of
  its fragment of GraphQL and its decoder together: the checks, the check suites, the stack and the summary of a pull
  request. A field is added to the GraphQL and the decoder in one place. `CliGitHub` runs gh and hands the output to
  the decoder of the query.
- Decoders build the model's records themselves; what the jq worked out is plain functions on what was decoded:
  the state of a check from its type, status, conclusion and state, when a pull request was last pushed to, the runs
  that wait to start. A record shaped like GitHub's answer is only made where such logic needs one, for the check
  suites. Not: decoding everything into records shaped like the answer first, which doubles the types.
- A query that reads all pages, of the pull requests and of the branches, runs gh with `--paginate --slurp`, so gh
  prints one array of all pages. That needs gh 2.48 or later, which README's requirements say. jazygit does not check
  the version beforehand; when an older gh fails with `unknown flag: --slurp`, the error says that gh 2.48 or later is
  needed, instead of gh's own message.
- The commands that are not GraphQL stay as they are and lose their `--jq`: the comments through `gh pr view --json`,
  whose filtering and order move to Java, the merges through REST's `merge-async`, the accounts through
  `gh auth status --json`, and the preview through `gh pr view --json`.
- What is read is what was read before. What fails the whole read still fails it, like one pull request of a list
  that cannot be read; what was left out is still left out: the timing of a check run that cannot be read, a review
  of a state that is not known, an entry of a stack whose pull request cannot be read, and a search hit without a
  number.

### `PullRequestDetails`

Its fields are grouped in records nested in it: `Branches` (head, base, the commit of the head), `Status` (state,
draft, mergeable, review, behind its base), `Size` (additions, deletions, changed files, commits) and `Activity`
(created, last pushed to, comments, review threads). Number, title, author, repository, labels, reviewers, checks and
stack stay on it, 12 fields in all. `withDraft` and `withBehindBase` rebuild only the `Status`. How the dialog reads
the fields is looked at before the split, in case they group better as its sections.

### Tests

Each query has its test, of its decoder on what GitHub answers, as JSON in text blocks; answers more than one query
reads, like the check suites, are fixtures they share. The cases of `CliGitHubJqTest` move to them, and so the tests no
longer need jq. `CliGitHubTest` keeps what is about how gh is run.

### Steps

One pull request each, the old parsing next to the new until the last:

1. The `jazygit-json` module, `Json` and `Decoder` with their tests, the ArchUnit rules, a native image built with
   it, and the module in ARCHITECTURE.md.
2. `GhProcess.readJson` and the simple commands: the accounts, the merges, the preview, how far behind its base.
3. The fragments of the checks, the check suites and the stack, with their logic in Java.
4. The branches, the check runs of branches, and the recent checks; gh 2.48 in README's requirements, and the error
   of an older gh that does not know `--slurp`.
5. The lists of pull requests: of the repository, mine, and those of the branches.
6. `PullRequestDetails` in its records, changing nothing it shows.
7. The details of a pull request.
8. The comments, and then what is left of the old parsing goes: `unescape`, the `*_FIELD_COUNT` constants,
   `CliGitHubJqTest` and jq in the dev shell. ARCHITECTURE.md's GitHub layer tells of the decoders instead of the
   jq and its lines. ADRs 7 and 35 get a reference to this one.

## Consequences

- A query and its decoder are in one class, and a field added to one side and not the other fails with its path.
- The logic the jq held is Java, tested without jq; jq is no longer needed to build or test jazygit.
- jazygit has a JSON library: Jackson's streaming parser, in one module, behind `Json.parse`.
- What the user is shown when gh's output cannot be read is the path and what was wrong, not the output.
- jazygit needs gh 2.48 or later for the pages of pull requests and branches; with an older gh those reads fail with
  an error that says so.
- A node of a type a fragment is not on is decoded by `__typename`, not by the fields it lacks.
- Not changed: what jazygit reads from GitHub, the commands that write, and how errors other than unreadable output
  are shown.
