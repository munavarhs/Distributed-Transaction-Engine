"""pyTest suite for the transaction log generator."""
import generate_logs as gen


def test_seeded_record_count():
    """Seeded mode must produce exactly (cycles * cycle_length) + filler records."""
    records = gen.generate_seeded(num_cycles=3, cycle_length=3, num_filler=10)
    assert len(records) == (3 * 3) + 10   # 19


def test_seeded_records_are_wellformed():
    """Every record has the required fields."""
    records = gen.generate_seeded(num_cycles=2, cycle_length=3, num_filler=5)
    for r in records:
        assert "txn_id" in r
        assert "holds" in r and isinstance(r["holds"], list)
        assert "wants" in r and isinstance(r["wants"], list)
        assert "timestamp" in r


def test_seeded_cycle_closes():
    """
    In a single seeded cycle, the accounts wanted should form a closed ring:
    the set of held accounts equals the set of wanted accounts.
    """
    records = gen.generate_seeded(num_cycles=1, cycle_length=3, num_filler=0)
    held = {r["holds"][0] for r in records}
    wanted = {r["wants"][0] for r in records}
    # A closed cycle: everything held is wanted by someone, and vice versa
    assert held == wanted


def test_random_record_count():
    """Random mode produces exactly the requested number of transactions."""
    records = gen.generate_random(num_txns=500, num_accounts=100)
    assert len(records) == 500


def test_random_no_self_wait():
    """No transaction should wait for an account it already holds."""
    records = gen.generate_random(num_txns=500, num_accounts=50)
    for r in records:
        assert r["holds"][0] != r["wants"][0]


def test_filler_does_not_close_cycle():
    """Filler transactions wait on brand-new accounts → should not form a ring."""
    records = gen.generate_seeded(num_cycles=0, cycle_length=3, num_filler=5)
    held = {r["holds"][0] for r in records}
    wanted = {r["wants"][0] for r in records}
    # With only filler, held and wanted sets should NOT be equal (no closed ring)
    assert held != wanted