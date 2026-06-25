package com.example.engine;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class WaitForGraphTest {

    // Helper: build a Transaction quickly
    private Transaction txn(String id, String holds, String wants) {
        Transaction t = new Transaction();
        t.txnId = id;
        t.holds = List.of(holds);
        t.wants = List.of(wants);
        t.timestamp = "2026-01-15T10:00:00";
        return t;
    }

    @Test
    void detectsSimpleTwoNodeCycle() {
        // T1 holds A wants B; T2 holds B wants A → one cycle
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"),
            txn("T2", "B", "A")
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);
        List<List<String>> cycles = graph.detectDeadlocks();

        assertEquals(1, cycles.size(), "Expected exactly one deadlock cycle");
    }

    @Test
    void detectsNoCycleWhenLinear() {
        // T1 wants B, T2 wants C, T3 wants nothing held → no cycle
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"),
            txn("T2", "B", "C"),
            txn("T3", "C", "D")   // D held by nobody → chain ends, no loop
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);
        List<List<String>> cycles = graph.detectDeadlocks();

        assertEquals(0, cycles.size(), "Linear wait chain should have no deadlock");
    }

    @Test
    void detectsThreeNodeCycle() {
        // T1->T2->T3->T1 ring
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"),
            txn("T2", "B", "C"),
            txn("T3", "C", "A")
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);
        List<List<String>> cycles = graph.detectDeadlocks();

        assertEquals(1, cycles.size(), "Expected one 3-node cycle");
        assertEquals(3, cycles.get(0).size(), "Cycle should contain 3 transactions");
    }

    @Test
    void doesNotDoubleCountSameCycle() {
        // Same ring — must be reported once, not three times
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"),
            txn("T2", "B", "C"),
            txn("T3", "C", "A")
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);
        List<List<String>> cycles = graph.detectDeadlocks();

        assertEquals(1, cycles.size(), "Cycle must be deduplicated, not counted per entry point");
    }
    @Test
    void buildCreatesCorrectEdges() {
        // T1 holds A wants B; T2 holds B wants C → edges T1->T2 (T2 holds B), T2->? (nobody holds C)
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"),
            txn("T2", "B", "C")
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);

        var adjacency = graph.getAdjacency();
        // T1 wants B, which T2 holds → T1 should have an edge to T2
        assertTrue(adjacency.get("T1").contains("T2"), "T1 should wait for T2");
        // T2 wants C, which nobody holds → T2 has no outgoing edge
        assertTrue(adjacency.get("T2").isEmpty(), "T2 should wait for no one");
    }

    @Test
    void handlesEmptyInput() {
        WaitForGraph graph = new WaitForGraph();
        graph.build(List.of());                       // no transactions
        List<List<String>> cycles = graph.detectDeadlocks();
        assertEquals(0, cycles.size(), "Empty graph has no deadlocks");
    }

    @Test
    void handlesSingleTransaction() {
        // One txn waiting on an account nobody else holds → no edge, no cycle
        WaitForGraph graph = new WaitForGraph();
        graph.build(List.of(txn("T1", "A", "B")));
        List<List<String>> cycles = graph.detectDeadlocks();
        assertEquals(0, cycles.size(), "Single transaction cannot deadlock");
    }

    @Test
    void detectsMultipleIndependentCycles() {
        // Two separate rings: (T1->T2->T1) and (T3->T4->T3)
        List<Transaction> txns = List.of(
            txn("T1", "A", "B"), txn("T2", "B", "A"),
            txn("T3", "C", "D"), txn("T4", "D", "C")
        );
        WaitForGraph graph = new WaitForGraph();
        graph.build(txns);
        List<List<String>> cycles = graph.detectDeadlocks();
        assertEquals(2, cycles.size(), "Two independent deadlocks expected");
    }

    @Test
    void ignoresSelfHeldWant() {
        // T1 wants an account it already holds → build() should not create a self-edge
        Transaction t = new Transaction();
        t.txnId = "T1";
        t.holds = List.of("A");
        t.wants = List.of("A");   // wants what it holds
        t.timestamp = "2026-01-15T10:00:00";

        WaitForGraph graph = new WaitForGraph();
        graph.build(List.of(t));
        assertTrue(graph.getAdjacency().get("T1").isEmpty(),
            "No self-edge when wanting a held account");
    }
}