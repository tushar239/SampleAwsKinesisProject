package producer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.amazonaws.services.kinesis.model.ResourceNotFoundException;
import model.StockTrade;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import utils.ConfigurationUtils;
import utils.CredentialUtils;

import java.nio.ByteBuffer;

/**
 * Continuously sends simulated stock trades to Kinesis
 */
public class StockTradesWriter {

    private static final Log LOG = LogFactory.getLog(StockTradesWriter.class);

    private static void checkUsage(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: " + StockTradesWriter.class.getSimpleName()
                    + " <stream name> <region>");
            System.exit(1);
        }
    }

    /**
     * Checks if the stream exists and is active
     *
     * @param kinesisClient Amazon Kinesis client instance
     * @param streamName    Name of stream
     */
    private static void validateStream(AmazonKinesis kinesisClient, String streamName) {
        try {
            DescribeStreamResult result = kinesisClient.describeStream(streamName);
            if (!"ACTIVE".equals(result.getStreamDescription().getStreamStatus())) {
                System.err.println("Stream " + streamName + " is not active. Please wait a few moments and try again.");
                System.exit(1);
            }
        } catch (ResourceNotFoundException e) {
            System.err.println("Stream " + streamName + " does not exist. Please create it in the console.");
            System.err.println(e);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error found while describing the stream " + streamName);
            System.err.println(e);
            System.exit(1);
        }
    }

    /**
     * Uses the Kinesis client to send the stock trade to the given stream.
     *
     * @param trade         instance representing the stock trade
     * @param kinesisClient Amazon Kinesis client
     * @param streamName    Name of stream
     */
    private static void sendStockTrade(StockTrade trade, AmazonKinesis kinesisClient,
                                       String streamName) {
        byte[] bytes = trade.toJsonAsBytes();
        // The bytes could be null if there is an issue with the JSON serialization by the Jackson JSON library.
        if (bytes == null) {
            LOG.warn("Could not get JSON bytes for stock trade");
            return;
        }

        // Putting records in a Stream one by one
        LOG.info("Putting trade: " + trade.toString());
        PutRecordRequest putRecordsRequest = new PutRecordRequest();
        putRecordsRequest.setStreamName(streamName);
        // We use the ticker symbol as the partition key, explained in the Supplemental Information section below.
        putRecordsRequest.setPartitionKey(trade.getTickerSymbol());
        putRecordsRequest.setData(ByteBuffer.wrap(bytes));

        try {
            kinesisClient.putRecord(putRecordsRequest);
        } catch (AmazonClientException ex) {
            LOG.warn("Error sending record to Amazon Kinesis.", ex);
        }

    }

    public static void main(String[] args) throws Exception {
        //checkUsage(args);

        String streamName = "MyKinesisStream";
        String regionName = "us-west-2";
        Region region = RegionUtils.getRegion(regionName);
        if (region == null) {
            System.err.println(regionName + " is not a valid AWS region.");
            System.exit(1);
        }

        AWSCredentials credentials = CredentialUtils.getCredentialsProvider().getCredentials();

        AmazonKinesisClientBuilder amazonKinesisClientBuilder = AmazonKinesisClientBuilder.standard();
        amazonKinesisClientBuilder.setCredentials(new AWSCredentialsProviderChain(CredentialUtils.getCredentialsProvider()));
        amazonKinesisClientBuilder.setClientConfiguration(ConfigurationUtils.getClientConfigWithUserAgent());
        amazonKinesisClientBuilder.setRegion(region.getName());
        AmazonKinesis kinesisClient = amazonKinesisClientBuilder.build();

        // Validate that the stream exists and is active
        validateStream(kinesisClient, streamName);

        // Repeatedly send stock trades with a 100 milliseconds wait in between
        StockTradeGenerator stockTradeGenerator = new StockTradeGenerator();
        while (true) {
            StockTrade trade = stockTradeGenerator.getRandomTrade();
            sendStockTrade(trade, kinesisClient, streamName);
            Thread.sleep(100);
        }
    }
}
