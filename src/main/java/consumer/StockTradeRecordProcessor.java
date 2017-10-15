package consumer;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason;
import com.amazonaws.services.kinesis.model.Record;
import model.StockTrade;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Processes records retrieved from stock trades stream.
 */
public class StockTradeRecordProcessor implements IRecordProcessor {
    private static final Log LOG = LogFactory.getLog(StockTradeRecordProcessor.class);
    private String kinesisShardId;

    // Reporting interval
    //private static final long REPORTING_INTERVAL_MILLIS = 60000L; // 1 minute
    private static final long REPORTING_INTERVAL_MILLIS = 1000L; // 1 minute
    private long nextReportingTimeInMillis;

    // Checkpointing interval
    private static final long CHECKPOINT_INTERVAL_MILLIS = 60000L; // 1 minute
    private long nextCheckpointTimeInMillis;

    // Aggregates stats for stock trades
    private StockStats stockStats = new StockStats();

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(String shardId) {
        LOG.info("Initializing record processor for shard: " + shardId);
        this.kinesisShardId = shardId;
        nextReportingTimeInMillis = System.currentTimeMillis() + REPORTING_INTERVAL_MILLIS;
        nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
    }

    /*
        Process data records.
        The Amazon Kinesis Client Library will invoke this method to deliver data records to the application.
        Upon fail over, the new instance will get records with sequence number > checkpoint position for each partition key.
    */
    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer checkpointer) {
        for (Record record : records) {
            // process record
            processRecord(record);
            System.out.println("processing..." + record.toString());
        }


        // If it is time to report stats as per the reporting interval, report stats
        if (System.currentTimeMillis() > nextReportingTimeInMillis) {
            reportStats();
            resetStats();
            nextReportingTimeInMillis = System.currentTimeMillis() + REPORTING_INTERVAL_MILLIS;
        }

        // Checkpoint once every checkpoint interval
        if (System.currentTimeMillis() > nextCheckpointTimeInMillis) {
            checkpoint(checkpointer);
            nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
        }
    }

    private void reportStats() {
        System.out.println("****** Shard " + kinesisShardId + " stats for last 1 minute ******\n" +
                stockStats + "\n" +
                "****************************************************************\n");

    }

    private void resetStats() {
        stockStats = new StockStats();
    }

    private void processRecord(Record record) {
        try {
            byte[] data = record.getData().array();
            StockTrade trade = StockTrade.fromJsonAsBytes(data);
            if (trade == null) {
                LOG.warn("Skipping record. Unable to parse record into StockTrade. Partition Key: " + record.getPartitionKey());
                return;
            }
            stockStats.addStockTrade(trade);
        } catch (Exception e) {
            System.out.println(e);
            // VERY IMP: Do something with records for which processing is failed.
            // Read README file's 'Some Kinesis Streams Records are Skipped When Using the Kinesis Client Library' section.
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason) {
        LOG.info("Shutting down record processor for shard: " + kinesisShardId);
        // VERY IMP: Important to checkpoint after reaching end of shard, so we can start processing data from child shards.
        // Read README file's IRecordProcessor's shutDown method notes.
        if (reason == ShutdownReason.TERMINATE) {
            checkpoint(checkpointer);
        }
    }

    // IMP: checkpoints are stored in DynamoDB. A table with same name as Stream name will be created in DynamodDB to store the statuses.
    private void checkpoint(IRecordProcessorCheckpointer checkpointer) {
        LOG.info("Checkpointing shard " + kinesisShardId);
        try {
            checkpointer.checkpoint();
        } catch (ShutdownException se) {
            // Ignore checkpoint if the processor instance has been shutdown (fail over).
            LOG.info("Caught shutdown exception, skipping checkpoint.", se);
        } catch (ThrottlingException e) {
            // Skip checkpoint when throttled. In practice, consider a backoff and retry policy.
            LOG.error("Caught throttling exception, skipping checkpoint.", e);
        } catch (InvalidStateException e) {
            // This indicates an issue with the DynamoDB table (check for table, provisioned IOPS).
            LOG.error("Cannot save checkpoint to the DynamoDB table used by the Amazon Kinesis Client Library.", e);
        }
    }
}
