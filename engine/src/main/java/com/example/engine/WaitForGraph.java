package com.example.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;

/**
 * Builds a wait-for graph from transactions and detects deadlock cycles.
 *
 * Node  = transaction id
 * Edge  A -> B  means "A is waiting for a resource that B holds"
 * Cycle = deadlock
 */
public class WaitForGraph {

    // Adjacency list: txnId -> list of txnIds it waits for
    private final Map<String, List<String>> adjacency = new HashMap<>();
    // Color states for DFS
    private enum Color { WHITE, GRAY, BLACK };

    public void build(List<Transaction> transactions) {
        // Step 1: map each account to the transaction that holds it
        Map<String, String> accountHolder = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.holds != null) {
                for (String acc : t.holds) {
                    accountHolder.put(acc, t.txnId);
                }
            }
        }

        // Step 2: for each "wants", draw an edge to whoever holds that account
        for (Transaction t : transactions) {
            adjacency.putIfAbsent(t.txnId, new ArrayList<>());
            if (t.wants != null) {
                for (String wantedAcc : t.wants) {
                    String holder = accountHolder.get(wantedAcc);
                    // edge only if someone holds it, and not a self-loop
                    if (holder != null && !holder.equals(t.txnId)) {
                        adjacency.get(t.txnId).add(holder);
                    }
                }
            }
        }
    }

    // Expose the graph (useful for tests / debugging)
    public Map<String, List<String>> getAdjacency() {
        return adjacency;
    }

    /**
     * Detects all deadlock cycles in the wait-for graph.
     * Returns a list of cycles; each cycle is the list of txn ids forming it.
     */
    public List<List<String>> detectDeadlocks() {
        Map<String, Color> color = new HashMap<>();
        for (String node : adjacency.keySet()) {
            color.put(node, Color.WHITE);
        }

        List<List<String>> cycles = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();   // tracks the current DFS path
        Set<String> reportedCycleKeys = new HashSet<>();  // avoid duplicate reports

        // DFS from every still-unvisited node (graph may be disconnected)
        for (String node : adjacency.keySet()) {
            if (color.get(node) == Color.WHITE) {
                dfs(node, color, path, cycles, reportedCycleKeys);
            }
        }
        return cycles;
    }

    private void dfs(String node,
                     Map<String, Color> color,
                     Deque<String> path,
                     List<List<String>> cycles,
                     Set<String> reportedCycleKeys) {

        color.put(node, Color.GRAY);   // mark: now on the active path
        path.addLast(node);

        for (String neighbor : adjacency.getOrDefault(node, List.of())) {
            Color nc = color.getOrDefault(neighbor, Color.WHITE);

            if (nc == Color.GRAY) {
                // Found a cycle! Extract it from the path: neighbor ... node
                List<String> cycle = extractCycle(path, neighbor);
                String key = canonicalKey(cycle);
                if (reportedCycleKeys.add(key)) {   // only report each cycle once
                    cycles.add(cycle);
                }
            } else if (nc == Color.WHITE) {
                dfs(neighbor, color, path, cycles, reportedCycleKeys);
            }
            // BLACK neighbor → already fully explored, no cycle through it
        }
        path.removeLast();
        color.put(node, Color.BLACK);  // mark: fully explored
    }

    // Pull the cycle out of the current path, starting from where it closes
    private List<String> extractCycle(Deque<String> path, String start) {
        List<String> cycle = new ArrayList<>();
        boolean collecting = false;
        for (String n : path) {            // ArrayDeque iterates first→last
            if (n.equals(start)) collecting = true;
            if (collecting) cycle.add(n);
        }
        return cycle;
    }

    // A stable key for a cycle regardless of where it "starts", so we don't
    // report the same ring multiple times (T1->T2->T3 == T2->T3->T1).
    private String canonicalKey(List<String> cycle) {
        List<String> sorted = new ArrayList<>(cycle);
        sorted.sort(String::compareTo);
        return String.join(",", sorted);
    }
}