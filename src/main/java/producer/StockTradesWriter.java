package producer;

import com.amazonaws.AmazonClientException;
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
 *
 * You need to create a Stream before you experiment a producer/consumer.
 * Read CreateAStream.docx
 *
 * O/P of this program will look something like below.
 INFO: Putting trade: ID 1006: BUY 6293 shares of XOM for $86.41
 Oct 14, 2017 11:34:30 PM producer.StockTradesWriter sendStockTrade
 INFO: Putting trade: ID 1007: BUY 9557 shares of GE for $26.94
 Oct 14, 2017 11:34:30 PM producer.StockTradesWriter sendStockTrade
 INFO: Putting trade: ID 1008: BUY 2528 shares of CVX for $122.42
 Oct 14, 2017 11:34:31 PM producer.StockTradesWriter sendStockTrade
 INFO: Putting trade: ID 1009: SELL 3811 shares of JNJ for $104.25
 Oct 14, 2017 11:34:31 PM producer.StockTradesWriter sendStockTrade
 INFO: Putting trade: ID 1010: SELL 3127 shares of FB for $80.72
 Oct 14, 2017 11:34:31 PM producer.StockTradesWriter sendStockTrade
 INFO: Putting trade: ID 1011: BUY 6626 shares of MSFT for $35.70

 */
public class StockTradesWriter {

    private static final Log LOG = LogFactory.getLog(StockTradesWriter.class);

    /*private static void checkUsage(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: " + StockTradesWriter.class.getSimpleName()
                    + " <stream name> <region>");
            System.exit(1);
        }
    }*/

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
    private static void sendStockTrade(StockTrade trade, AmazonKinesis kinesisClient, String streamName) {
        // The PutRecord API expects a byte array, and you need to convert trade to JSON format. This single line of code performs that operation:
        byte[] bytes = trade.toJsonAsBytes();

        /*
            StockTrade Record
            {
              "tickerSymbol": "AMZN",
              "tradeType": "BUY",
              "price": 395.87,
              "quantity": 16,
              "id": 3567129045
            }
         */
        // The bytes could be null if there is an issue with the JSON serialization by the Jackson JSON library.
        if (bytes == null) {
            LOG.warn("Could not get JSON bytes for stock trade");
            return;
        }

        // There are 3 different ways of putting records in stream.
        // Here, we are using PutRecord API. PutRecords is for putting records in stream in bulk.
        // KPL (Kinesis Producer Library) is better because it takes care of buffering records before putting them.
        // Read README file's 'There are many ways to put records in Kinesis Stream' section.

        // Putting records in a Stream one by one
        LOG.info("Putting trade: " + trade.toString());
        PutRecordRequest putRecordsRequest = new PutRecordRequest();
        putRecordsRequest.setStreamName(streamName);
        // We use the ticker symbol as the partition key.
        // The example uses a stock ticket as a partition key, which maps the record to a specific shard. In practice, you should have hundreds or thousands of partition keys per shard such that records are evenly dispersed across your stream.
        // If you want, you can configure a consumer to read data from a specific shard using partition key.
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

        AmazonKinesis kinesisClient = AmazonKinesisClientBuilder.standard()
                .withCredentials(CredentialUtils.getCredentialsProvider())
                .withClientConfiguration(ConfigurationUtils.getClientConfigWithUserAgent())
                .withRegion(region.getName())
                .build();

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
