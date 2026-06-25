package com.example.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class TransactionConsumer {

    private static final String TOPIC = "transactions";
    private static final String BOOTSTRAP = "localhost:9092";

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "txn-postgres-writer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // start at offset 0

        ObjectMapper mapper = new ObjectMapper();
        TransactionWriter writer = new TransactionWriter();
        int totalWritten = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(TOPIC));
            System.out.println("Consumer subscribed to '" + TOPIC + "'. Polling...");

            int emptyPolls = 0;
            while (emptyPolls < 5) {   // stop after 5 consecutive empty polls (drained)
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(1000));

                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                emptyPolls = 0;

                // Collect this poll's records into a batch, then write once
                List<Transaction> batch = new ArrayList<>();
                for (ConsumerRecord<String, String> record : records) {
                    Transaction t = mapper.readValue(record.value(), Transaction.class);
                    batch.add(t);
                }

                int written = writer.writeBatch(batch);
                totalWritten += written;
                System.out.println("Wrote batch of " + written
                        + " (total: " + totalWritten + ")");
            }
        }

        System.out.println("Done. Total records written to Postgres: " + totalWritten);
    }
}