package com.example.engine;

import java.util.List;

public class LoadToDb {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "../tooling/data/transactions.jsonl";

        List<Transaction> txns = DeadlockDetector.loadTransactions(path);
        System.out.println("Loaded " + txns.size() + " transactions from file");

        TransactionWriter writer = new TransactionWriter();
        long start = System.currentTimeMillis();
        int written = writer.writeBatch(txns);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Wrote " + written + " rows in " + elapsed + " ms");
    }
}