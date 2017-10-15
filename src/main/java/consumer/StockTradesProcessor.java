package consumer;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import utils.ConfigurationUtils;
import utils.CredentialUtils;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses the Kinesis Client Library (KCL) to continuously consume and process stock trade
 * records from the stock trades stream. KCL monitors the number of shards and creates
 * record processor instances to read and process records from each shard. KCL also
 * load balances shards across all the instances of this processor.
 *
 * IMP:
 * It is possible that consumer can read same record multiple times (read README.md's 'Handling Duplicate Records' section).
 * It's upto the consumer how to handle duplicate records.
 *
 * O/P of this program will look something like this:
 *
     ****** Shard shardId-000000000002 stats for last 1 minute ******
     Most popular stock being bought: JPM, 21 buys.
     Most popular stock being sold: GE, 11 sells.
     ****************************************************************

     ****** Shard shardId-000000000001 stats for last 1 minute ******
     Most popular stock being bought: XOM, 16 buys.
     Most popular stock being sold: CHL, 10 sells.
     ****************************************************************

     ****** Shard shardId-000000000000 stats for last 1 minute ******
     Most popular stock being bought: FB, 16 buys.
     Most popular stock being sold: AMZN, 7 sells.
     ****************************************************************

     ****** Shard shardId-000000000002 stats for last 1 minute ******
     Most popular stock being bought: GE, 14 buys.
     Most popular stock being sold: T, 7 sells.
     ****************************************************************

     ****** Shard shardId-000000000001 stats for last 1 minute ******
     Most popular stock being bought: RDS.A, 17 buys.
     Most popular stock being sold: VZ, 11 sells.
     ****************************************************************

     ****** Shard shardId-000000000000 stats for last 1 minute ******
     Most popular stock being bought: WFC, 18 buys.
     Most popular stock being sold: WFC, 12 sells.
     ****************************************************************
 */
public class StockTradesProcessor {
    private static final Log LOG = LogFactory.getLog(StockTradesProcessor.class);

    private static final Logger ROOT_LOGGER = Logger.getLogger("");
    private static final Logger PROCESSOR_LOGGER =
            Logger.getLogger("com.amazonaws.services.kinesis.samples.stocktrades.processor");

/*
    private static void checkUsage(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: " + StockTradesProcessor.class.getSimpleName()
                    + " <application name> <stream name> <region>");
            System.exit(1);
        }
    }
*/

    /**
     * Sets the global log level to WARNING and the log level for this package to INFO,
     * so that we only see INFO messages for this processor. This is just for the purpose
     * of this tutorial, and should not be considered as best practice.
     */
    private static void setLogLevels() {
        ROOT_LOGGER.setLevel(Level.WARNING);
        PROCESSOR_LOGGER.setLevel(Level.INFO);
    }

    public static void main(String[] args) throws Exception {
        //checkUsage(args);

        // https://aws.amazon.com/kinesis/streams/faqs/
        // (IMP) All workers associated with the same application name are assumed to be working together on the same Amazon Kinesis stream.
        // If you run an additional instance of the same application code, but with a different application name, KCL treats the second instance as an entirely separate application also operating on the same stream.
        String applicationName = "MyKinesisStreamConsumer";
        String streamName = "MyKinesisStream";
        String regionName = "us-west-2";

        Region region = RegionUtils.getRegion(regionName);
        if (region == null) {
            System.err.println(regionName + " is not a valid AWS region.");
            System.exit(1);
        }

        setLogLevels();

        AWSCredentialsProvider credentialsProvider = CredentialUtils.getCredentialsProvider();

        String workerId = String.valueOf(UUID.randomUUID());
        KinesisClientLibConfiguration kclConfig =
                new KinesisClientLibConfiguration(applicationName, streamName, credentialsProvider, workerId)
                        .withRegionName(region.getName())
                        .withCommonClientConfig(ConfigurationUtils.getClientConfigWithUserAgent());
                        // read README.md' "Handling Startup, Shutdown and Throttling" section to understand why TRIM_HORIZON should be set as startint position to read the records from the stream.
                        //.withInitialPositionInStream(InitialPositionInStream.TRIM_HORIZON);

        IRecordProcessorFactory recordProcessorFactory = new StockTradeRecordProcessorFactory();

        // Create the KCL worker with the stock trade record processor factory
        Worker worker = new Worker(recordProcessorFactory, kclConfig);

        int exitCode = 0;
        try {
            worker.run();
        } catch (Throwable t) {
            LOG.error("Caught throwable while processing data.", t);
            exitCode = 1;
        }
        System.exit(exitCode);

    }
}
