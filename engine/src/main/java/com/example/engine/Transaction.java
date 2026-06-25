package com.example.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// One transaction log record, parsed from a JSON line.
public class Transaction {

    @JsonProperty("txn_id")
    public String txnId;

    public List<String> holds;
    public List<String> wants;
    public String timestamp;

    public Transaction() {}   // Jackson needs a no-arg constructor

    @Override
    public String toString() {
        return txnId + " holds=" + holds + " wants=" + wants;
    }
}