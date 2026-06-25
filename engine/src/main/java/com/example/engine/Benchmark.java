package com.example.engine;

import java.util.Comparator;
import java.util.List;

public class Benchmark {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "../tooling/data/transactions.jsonl";
        List<Transaction> txns = DeadlockDetector.loadTransactions(path);
        System.out.println("Loaded " + txns.size() + " transactions\n");

        TransactionWriter writer = new TransactionWriter();

        // --- NAIVE: one-by-one inserts ---
        clearTable();
        long t1 = System.currentTimeMillis();
        writer.writeOneByOne(txns);
        long naiveMs = System.currentTimeMillis() - t1;
        System.out.println("Naive (per-row commit):  " + naiveMs + " ms");

        // --- OPTIMIZED: batched inserts ---
        clearTable();
        long t2 = System.currentTimeMillis();
        writer.writeBatch(txns);
        long batchMs = System.currentTimeMillis() - t2;
        System.out.println("Optimized (batched):     " + batchMs + " ms");

        // --- The measured acceleration ---
        double speedup = ((double)(naiveMs - batchMs) / naiveMs) * 100;
        System.out.printf("Throughput acceleration: %.1f%%%n", speedup);

        // --- SORTING: order by txn_time (in-memory demo) ---
        long t3 = System.currentTimeMillis();
        txns.sort(Comparator.comparing(t -> t.timestamp));
        long sortMs = System.currentTimeMillis() - t3;
        System.out.println("\nSorted " + txns.size() + " records by timestamp in " + sortMs + " ms");
    }

    // Truncate the table between runs so each test starts clean
    static void clearTable() throws Exception {
        try (var conn = java.sql.DriverManager.getConnection(
                "jdbc:postgresql://localhost:5433/txndb", "txnuser", "txnpass");
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE transactions RESTART IDENTITY");
        }
    }
}