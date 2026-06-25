package com.example.engine;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class DeadlockDetector {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: DeadlockDetector <path-to-jsonl>");
            return;
        }
        String path = args[0];

        // 1. Load transactions from the JSON Lines file
        List<Transaction> transactions = loadTransactions(path);
        System.out.println("Loaded " + transactions.size() + " transactions");

        // 2. Build the wait-for graph
        WaitForGraph graph = new WaitForGraph();
        graph.build(transactions);

        // 3. Detect deadlocks
        List<List<String>> deadlocks = graph.detectDeadlocks();

        // 4. Report
        System.out.println("Detected " + deadlocks.size() + " deadlock cycle(s):");
        for (List<String> cycle : deadlocks) {
            System.out.println("  CYCLE: " + String.join(" -> ", cycle)
                + " -> " + cycle.get(0));   // close the ring visually
        }
    }

    // Read JSON Lines: one Transaction per line. Streaming-friendly (line by line).
    static List<Transaction> loadTransactions(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Transaction> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                result.add(mapper.readValue(line, Transaction.class));
            }
        }
        return result;
    }
}