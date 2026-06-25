"""
Generates mock transaction logs for the deadlock detection engine.

Two modes:
  random  - realistic bulk data; deadlocks may occur by chance (unknown count)
  seeded  - plants a known number of guaranteed cycles (for testing correctness)
"""
import json
import random
import argparse
from datetime import datetime, timedelta


def make_record(txn_id, holds, wants, ts):
    """One transaction log record: what it holds, what it's waiting for."""
    return {
        "txn_id": txn_id,
        "holds": holds,
        "wants": wants,
        "timestamp": ts.isoformat(),
    }


def generate_random(num_txns, num_accounts):
    """
    Realistic data. Each transaction holds 1 account and wants another.
    Cycles (deadlocks) may form by chance — we don't control how many.
    """
    records = []
    base_time = datetime(2026, 1, 15, 10, 0, 0)
    for i in range(num_txns):
        held = f"ACC_{random.randint(1, num_accounts)}"
        wanted = f"ACC_{random.randint(1, num_accounts)}"
        while wanted == held:                 # don't wait on what you already hold
            wanted = f"ACC_{random.randint(1, num_accounts)}"
        records.append(make_record(
            f"T{i+1}", [held], [wanted],
            base_time + timedelta(seconds=i)))
    return records


def generate_seeded(num_cycles, cycle_length, num_filler):
    """
    Plants `num_cycles` guaranteed deadlock cycles, each of `cycle_length`
    transactions, plus filler transactions that don't deadlock.
    The answer: exactly num_cycles deadlocks.
    """
    records = []
    base_time = datetime(2026, 1, 15, 10, 0, 0)
    txn_counter = 0
    acc_counter = 0

    # Build each cycle: T_a holds ACC_a wants ACC_b; T_b holds ACC_b wants ACC_c; ... back to ACC_a
    for c in range(num_cycles):
        cycle_accounts = [f"ACC_{acc_counter + j}" for j in range(cycle_length)]
        acc_counter += cycle_length
        for j in range(cycle_length):
            held = cycle_accounts[j]
            wanted = cycle_accounts[(j + 1) % cycle_length]   # wrap → closes the cycle
            txn_counter += 1
            records.append(make_record(
                f"T{txn_counter}", [held], [wanted],
                base_time + timedelta(seconds=txn_counter)))

    # Filler: linear waits, no cycle (T holds ACC_x, wants a brand-new ACC_y)
    for f in range(num_filler):
        held = f"ACC_{acc_counter}"
        wanted = f"ACC_{acc_counter + 1}"
        acc_counter += 2
        txn_counter += 1
        records.append(make_record(
            f"T{txn_counter}", [held], [wanted],
            base_time + timedelta(seconds=txn_counter)))

    return records


def main():
    parser = argparse.ArgumentParser(description="Generate transaction logs")
    parser.add_argument("--mode", choices=["random", "seeded"], default="random")
    parser.add_argument("--count", type=int, default=1000,
                        help="number of transactions (random mode)")
    parser.add_argument("--accounts", type=int, default=200,
                        help="distinct accounts (random mode)")
    parser.add_argument("--cycles", type=int, default=3,
                        help="number of deadlock cycles (seeded mode)")
    parser.add_argument("--cycle-length", type=int, default=3,
                        help="transactions per cycle (seeded mode)")
    parser.add_argument("--filler", type=int, default=20,
                        help="non-deadlocked filler txns (seeded mode)")
    parser.add_argument("--out", default="data/transactions.jsonl")
    args = parser.parse_args()

    if args.mode == "random":
        records = generate_random(args.count, args.accounts)
    else:
        records = generate_seeded(args.cycles, args.cycle_length, args.filler)

    # Write as JSON Lines (one JSON object per line) — the streaming-friendly format
    with open(args.out, "w") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")

    print(f"Wrote {len(records)} records to {args.out} (mode={args.mode})")
    if args.mode == "seeded":
        print(f"  Known answer: {args.cycles} deadlock cycle(s) planted")


if __name__ == "__main__":
    main()