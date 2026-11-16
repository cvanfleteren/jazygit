"""The shares the CI machines split the tests in: no app, only what shards.py works out."""
from shards import merged, shares

TESTS = [f"test_{name}.py::test" for name in "abcdefghijklmnopqrstuvwxyz"]


def test_the_shares_run_every_test_once_in_the_order_collected():
    # Most tests have a duration; two have none, and one duration is of a test no longer collected.
    durations = {test: float(1 + index % 7) for index, test in enumerate(TESTS[:-2])} | {"test_gone.py::test": 99.0}

    split = shares(TESTS, durations, 6)

    assert sorted(test for share in split for test in share) == sorted(TESTS)
    assert all(share == sorted(share, key=TESTS.index) for share in split)


def test_the_shares_take_about_as_long():
    durations = {test: float(1 + index % 7) for index, test in enumerate(TESTS)}

    totals = [sum(durations[test] for test in share) for share in shares(TESTS, durations, 6)]

    assert max(totals) - min(totals) <= max(durations.values())


def test_without_durations_a_share_is_every_nth_test():
    assert shares(TESTS, {}, 6) == [TESTS[index::6] for index in range(6)]


def test_more_shares_than_tests_leaves_some_empty():
    split = shares(TESTS[:2], {TESTS[0]: 3.0, TESTS[1]: 1.0}, 4)

    assert sorted(map(len, split)) == [0, 0, 1, 1]


def test_tests_that_took_no_time_spread_over_the_shares():
    split = shares(TESTS, dict.fromkeys(TESTS, 0.0), 6)

    assert sorted(map(len, split)) == [4, 4, 4, 4, 5, 5]


def test_a_merged_duration_moves_part_of_the_way_and_a_test_that_did_not_run_is_dropped():
    assert merged({"a": 10.0, "gone": 3.0}, {"a": 20.0, "new": 5.0}) == {"a": 13.0, "new": 5.0}
