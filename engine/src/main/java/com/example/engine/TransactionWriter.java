package com.example.engine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Writes transaction records into Postgres via JDBC.
 * Uses batch inserts for throughput.
 */
public class TransactionWriter {

    // Note: port 5433 — your container's mapped host port
    private static final String URL = "jdbc:postgresql://localhost:5433/txndb";
    private static final String USER = "txnuser";
    private static final String PASSWORD = "txnpass";

    public int writeBatch(List<Transaction> transactions) throws Exception {
        String sql = "INSERT INTO transactions (txn_id, holds, wants, txn_time) "
                   + "VALUES (?, ?, ?, ?::timestamp)";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);   // batch: commit once at the end, not per row

            for (Transaction t : transactions) {
                stmt.setString(1, t.txnId);
                stmt.setString(2, String.join(",", t.holds));
                stmt.setString(3, String.join(",", t.wants));
                stmt.setString(4, t.timestamp);
                stmt.addBatch();          // queue this insert
            }

            int[] results = stmt.executeBatch();   // send all queued inserts at once
            conn.commit();                          // commit the whole batch
            return results.length;
        }
    }

    // NAIVE: insert one row at a time, auto-commit each. The slow baseline.
    public int writeOneByOne(List<Transaction> transactions) throws Exception {
        String sql = "INSERT INTO transactions (txn_id, holds, wants, txn_time) "
                   + "VALUES (?, ?, ?, ?::timestamp)";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(true);   // commit after EVERY insert — the slow part

            int count = 0;
            for (Transaction t : transactions) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, t.txnId);
                    stmt.setString(2, String.join(",", t.holds));
                    stmt.setString(3, String.join(",", t.wants));
                    stmt.setString(4, t.timestamp);
                    stmt.executeUpdate();   // execute + implicitly commit, one row
                    count++;
                }
            }
            return count;
        }
    }
}