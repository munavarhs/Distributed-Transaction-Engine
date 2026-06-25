package com.example.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;

public class TransactionProducer {

    private static final String TOPIC = "transactions";
    private static final String BOOTSTRAP = "localhost:9092";

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "../tooling/data/transactions.jsonl";
        List<Transaction> txns = DeadlockDetector.loadTransactions(path);
        System.out.println("Loaded " + txns.size() + " transactions to publish");

        // --- Producer configuration ---
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");   // wait for broker confirmation

        ObjectMapper mapper = new ObjectMapper();
        int sent = 0;

        // try-with-resources: producer closes (and flushes) automatically
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (Transaction t : txns) {
                String json = mapper.writeValueAsString(t);  // serialize record to JSON
                // key = txn_id, value = the JSON record
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(TOPIC, t.txnId, json);
                producer.send(record);   // append to the topic (async)
                sent++;
            }
            producer.flush();   // ensure everything is actually sent before we exit
        }

        System.out.println("Published " + sent + " records to topic '" + TOPIC + "'");
    }
}