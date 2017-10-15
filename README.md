This example project is created from

    http://docs.aws.amazon.com/streams/latest/dev/learning-kinesis-module-one.html

Create a Stream using AWS Console
---------------------------------
Read CreateAStream.docx

IAM Policy
----------
http://docs.aws.amazon.com/streams/latest/dev/learning-kinesis-module-one-create-stream.html

If you are going to run your producer and consumer code from EC2 instances, then you need to give those EC2 instances proper Roles. Producer should be able to access Kinesis Stream and consumer should be able to write data to dynamodb and access cloudwatch to put metric data.
For this example, we need a user with access key that has both producer and consumer kind of permissions because we are not going use EC2 for this example.

You need an access key with min following policy.
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Stmt123",
      "Effect": "Allow",
      "Action": [
        "kinesis:DescribeStream",
        "kinesis:PutRecord",
        "kinesis:PutRecords",
        "kinesis:GetShardIterator",
        "kinesis:GetRecords"
      ],
      "Resource": [
        "arn:aws:kinesis:us-west-2:123:stream/StockTradeStream"
      ]
    },
    {
      "Sid": "Stmt456",
      "Effect": "Allow",
      "Action": [
        "dynamodb:*"
      ],
      "Resource": [
        "arn:aws:dynamodb:us-west-2:123:table/StockTradesProcessor"
      ]
    },
    {
      "Sid": "Stmt789",
      "Effect": "Allow",
      "Action": [
        "cloudwatch:PutMetricData"
      ],
      "Resource": [
        "*"
      ]
    }
  ]
}


There are many ways to put records in Kinesis Stream
----------------------------------------------------
- PutRecord API
- PutRecords API
- KPL (Kinesis Producer Library)
- Kinesis Agent

    Using PutRecord API
    -------------------
    This example is using it. It is putting records one by one in the stream.

    Using PutRecords API (Bulk PutRecords)
    --------------------------------------

    http://docs.aws.amazon.com/streams/latest/dev/developing-producers-with-sdk.html#kinesis-using-sdk-java-putrecords

    PutRecordsRequest putRecordsRequest  = new PutRecordsRequest();
    putRecordsRequest.setStreamName(streamName);
    List<PutRecordsRequestEntry> putRecordsRequestEntryList  = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
        PutRecordsRequestEntry putRecordsRequestEntry  = new PutRecordsRequestEntry();
        putRecordsRequestEntry.setData(ByteBuffer.wrap(String.valueOf(i).getBytes()));
        putRecordsRequestEntry.setPartitionKey(String.format("partitionKey-%d", i));
        putRecordsRequestEntryList.add(putRecordsRequestEntry);
    }

    putRecordsRequest.setRecords(putRecordsRequestEntryList);
    PutRecordsResult putRecordsResult  = kinesisClient.putRecords(putRecordsRequest);
    System.out.println("Put Result" + putRecordsResult);

    Handing Failures

        Records that were unsuccessfully processed can be included in subsequent PutRecords requests.
        First, check the FailedRecordCount parameter in the putRecordsResult to confirm if there are failed records in the request.
        If so, each putRecordsEntry that has an ErrorCode that is not null should be added to a subsequent request. For an example of this type of handler, refer to the following code.

        {
            "FailedRecordCount”: 1,
            "Records": [
                {
                    "SequenceNumber": "21269319989900637946712965403778482371",
                    "ShardId": "shardId-000000000001"

                },
                {
                    “ErrorCode":”ProvisionedThroughputExceededException”,
                    “ErrorMessage": "Rate exceeded for shard shardId-000000000001 in stream exampleStreamName under account 111111111111."

                },
                {
                    "SequenceNumber": "21269319989999637946712965403778482985",
                    "ShardId": "shardId-000000000002"
                }
            ]
        }
        
    while (putRecordsResult.getFailedRecordCount() > 0) {
        final List<PutRecordsRequestEntry> failedRecordsList = new ArrayList<>();
        final List<PutRecordsResultEntry> putRecordsResultEntryList = putRecordsResult.getRecords();
        for (int i = 0; i < putRecordsResultEntryList.size(); i++) {
            final PutRecordsRequestEntry putRecordRequestEntry = putRecordsRequestEntryList.get(i);
            final PutRecordsResultEntry putRecordsResultEntry = putRecordsResultEntryList.get(i);
            if (putRecordsResultEntry.getErrorCode() != null) {
                failedRecordsList.add(putRecordRequestEntry);
            }
        }
        putRecordsRequestEntryList = failedRecordsList;
        putRecordsRequest.setRecords(putRecordsRequestEntryList);
        putRecordsResult = amazonKinesisClient.putRecords(putRecordsRequest);
    }

    If you exceed number of records to be sent in one request, you get ‘ProvisionedThroughputExccededException’.
    In this case, you need to batch these failed records in the next request. Retrying looks like a big pain. Use KPL (Kinesis Producer Library) to retry automatically instead of using lower level of PutRecords API.
    KPL uses PutRecords API internally.

    Writing to Amazon Kinesis Streams Using Kinesis Agent
    -----------------------------------------------------

    http://docs.aws.amazon.com/streams/latest/dev/writing-with-agents.html

    Kinesis Agent is a stand-alone Java software application that offers an easy way to collect and send data to Kinesis Streams.
    The agent continuously monitors a set of files and sends new data to your stream. The agent handles file rotation, checkpointing, and retry upon failures. It delivers all of your data in a reliable, timely, and simple manner.
    It also emits Amazon CloudWatch metrics to help you better monitor and troubleshoot the streaming process.

    Using KPL (Kinesis Producer Library)
    ------------------------------------

    http://docs.aws.amazon.com/streams/latest/dev/kinesis-kpl-writing.html

    http://docs.aws.amazon.com/streams/latest/dev/developing-producers-with-kpl.html

    Note that the KPL is different from the Kinesis Streams API that is available in the AWS SDKs. The Kinesis Streams API helps you manage many aspects of Kinesis Streams (including creating streams, resharding, and putting and getting records), while the KPL provides a layer of abstraction specifically for ingesting data.
    The KPL is an easy-to-use, highly configurable library that helps you write to a Kinesis stream. It acts as an intermediary between your producer application code and the Kinesis Streams API actions. The KPL performs the following primary tasks:

    - Writes to one or more Kinesis streams with an automatic and configurable retry mechanism
    - Collects records and uses PutRecords to write multiple records to multiple shards per request
    - (IMP) Aggregates user records to increase payload size and improve throughput
    - Integrates seamlessly with the Kinesis Client Library (KCL) to de-aggregate batched records on the consumer
    - Submits Amazon CloudWatch metrics on your behalf to provide visibility into producer performance

    Performance Benefits
        The KPL can help build high-performance producers. Consider a situation where your Amazon EC2 instances serve as a proxy for collecting 100-byte events from hundreds or thousands of low power devices and writing records into an Kinesis stream. These EC2 instances must each write thousands of events per second to your Kinesis stream. To achieve the throughput needed, producers must implement complicated logic such as batching or multithreading, in addition to retry logic and record de-aggregation at the consumer side. The KPL performs all of these tasks for you.

    Consumer-side Ease of Use
        For consumer-side developers using the KCL in Java, the KPL integrates without additional effort. When the KCL retrieves an aggregated Kinesis Streams record consisting of multiple KPL user records, it automatically invokes the KPL to extract the individual user records before returning them to the user.
        For consumer-side developers who do not use the KCL but instead use the API operation GetRecords directly, a KPL Java library is available to extract the individual user records before returning them to the user.

    Producer Monitoring
        You can collect, monitor, and analyze your Kinesis Streams producers using Amazon CloudWatch and the KPL. The KPL emits throughput, error, and other metrics to CloudWatch on your behalf, and is configurable to monitor at the stream, shard, or producer level.

    Asynchronous Architecture
        Because the KPL may buffer records before sending them to Kinesis Streams, it does not force the caller application to block and wait for a confirmation that the record has arrived at the server before continuing execution.
        A call to put a record into the KPL always returns immediately and does not wait for the record to be sent or a response to be received from the server. Instead, a Future object is created that receives the result of sending the record to Kinesis Streams at a later time.
        This is the same behavior as asynchronous clients in the AWS SDK.

    When Not To Use the KPL?
        The KPL can incur an additional processing delay of up to RecordMaxBufferedTime within the library (user-configurable). Larger values of RecordMaxBufferedTime results in higher packing efficiencies and better performance. Applications that cannot tolerate this additional delay may need to use the AWS SDK directly.

Stream Consumer
---------------

You can have Apache Storm/Spark etc as a Consumer of Kinesis Stream. There is a special Spout available in Apache Storm for consuming records from Kinesis Stream.
OR
you can write your own consumer using KCL (Kinesis Consumer Library).

There are many low-level consumer apis available like GetRecords, but KCL abstracts this low-level api and provides you many other advantages.
http://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetRecords.html

You can use the Kinesis Client Library (KCL) to simplify parallel processing of the stream by a fleet of workers running on a fleet of EC2 instances.
The KCL simplifies writing code to read from the shards in the stream and ensures that there is a worker allocated to every shard in the stream. The KCL also provides help with fault tolerance by providing checkpointing capabilities.

    KCL (Kinesis Consumer Library)
    ------------------------------
         http://docs.aws.amazon.com/streams/latest/dev/developing-consumers-with-kcl.html
         
         The KCL takes care of many of the complex tasks associated with distributed computing, such as load-balancing across multiple instances, responding to instance failures, checkpointing processed records, and reacting to resharding. The KCL enables you to focus on writing record processing logic.
         Note that the KCL is different from the Kinesis Streams API that is available in the AWS SDKs. The Kinesis Streams API helps you manage many aspects of Kinesis Streams (including creating streams, resharding, and putting and getting records), while the KCL provides a layer of abstraction specifically for processing data in a consumer role.
    
         http://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-implementation-app-java.html
         https://github.com/aws/aws-sdk-java/tree/master/src/samples
    
        Consumer's responsibility
            - Connects to stream and enumerates the shards
            - Coordinates shard associations with other workers (if any)
            - Instantiates record processor for every shart it manages
            - Pulls data records from the shard (Using GetRecords api internally)
            - Pushes the records to the corresponding record processor (Each Consumer has one Worker and Worker creates one RecordProcessor is created for each shard. For parallel processing of records in one shard, you can create multiple consumers(multiple EC2, each with one consumer).)
            - (IMP) Checkpoints processed records
            - Balances shard associations when the worker instance count changes
            - Balances shard associations when shards are split or merged
    
        IRecordProcessor Methods
    
    
             initialize
    
                 The KCL calls the initialize method when the record processor is instantiated, passing a specific shard ID as a parameter. This record processor processes only this shard and typically.
    
             processRecords
    
                 The KCL calls the processRecords method, passing a list of data record from the shard specified by the initialize(shardId) method. The record processor processes the data in these records according to the semantics of the consumer. For example, the worker might perform a transformation on the data and then store the result in an Amazon S3 bucket.
                 In addition to the data itself, the record also contains a sequence number and partition key. The worker can use these values when processing the data. For example, the worker could choose the S3 bucket in which to store the data based on the value of the partition key. The Record class exposes the following methods that provide access to the record's data, sequence number, and partition key.
    
                 record.getData()
                 record.getSequenceNumber()
                 record.getPartitionKey()
    
                 Kinesis Streams requires the record processor to keep track of the records that have already been processed in a shard. The KCL takes care of this tracking for you by passing a checkpointer (IRecordProcessorCheckpointer) to processRecords. The record processor calls the checkpoint method on this interface to inform the KCL of how far it has progressed in processing the records in the shard. In the event that the worker fails, the KCL uses this information to restart the processing of the shard at the last known processed record.
    
                 In the case of a split or merge operation, the KCL won't start processing the new shards until the processors for the original shards have called checkpoint to signal that all processing on the original shards is complete.
    
                 If you don't pass a parameter, the KCL assumes that the call to checkpoint means that all records have been processed, up to the last record that was passed to the record processor. Therefore, the record processor should call checkpoint only after it has processed all the records in the list that was passed to it. Record processors do not need to call checkpoint on each call to processRecords. A processor could, for example, call checkpoint on every third call to processRecords. You can optionally specify the exact sequence number of a record as a parameter to checkpoint. In this case, the KCL assumes that all records have been processed up to that record only.
    
                 The KCL relies on processRecords to handle any exceptions that arise from processing the data records. If an exception is thrown from processRecords, the KCL skips over the data records that were passed prior to the exception; that is, these records are not re-sent to the record processor that threw the exception or to any other record processor in the consumer.
    
             shutdown
    
                 The KCL calls the shutdown method either when processing ends (the shutdown reason is TERMINATE) or the worker is no longer responding (the shutdown reason is ZOMBIE).
    
                 public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason)
    
                 Processing ends when the record processor does not receive any further records from the shard, because either the shard was split or merged, or the stream was deleted.
    
                 The KCL also passes a IRecordProcessorCheckpointer interface to shutdown. If the shutdown reason is TERMINATE, the record processor should finish processing any data records, and then call the checkpoint method on this interface.
    
        Worker and RecordProcessor on Consumer side
    
            you can deploy your KCL application on multiple EC2 instances. Each EC2 instance runs one worker. Every worker runs number of record processors (1 record processor per shard). Workers continuously keep polling to know number of shards and accordingly increases or decreases number of record processors.
    

Important Concepts
------------------

    Partition Key
    -------------
        http://docs.aws.amazon.com/streams/latest/dev/learning-kinesis-module-one-consumer.html#learning-kinesis-module-one-consumer-supplement

        A partition key is used by Kinesis Streams as a mechanism to split records across multiple shards (when there is more than one shard in the stream). The same partition key always routes to the same shard. This allows the consumer that processes a particular shard to be designed with the assumption that records with the same partition key would only be sent to that consumer, and no records with the same partition key would end up at any other consumer.
        Therefore, a consumer's worker can aggregate all records with the same partition key without worrying that it might be missing needed data.

        You can supply random partition key to avoid hotspot problem. Data will be spread across all shards.
        If you want all data belonging to particular client (or business function), then you can supply partition key accordingly that writes all related data in same partition, so that later on when client needs data, map reduce becomes inexpensive.

    Sequence Number
    --------------
        http://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetShardIterator.html

        A sequence number is the identifier associated with every record ingested in the stream, and is assigned when a record is put into the stream. Each stream has one or more shards.
        It is necessory for ordering the records.

    ShardIterator
    -------------
        http://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetShardIterator.html

        Gets an Amazon Kinesis shard iterator. A shard iterator expires five minutes after it is returned to the requester.

        A shard iterator specifies the shard position from which to start reading data records sequentially. The position is specified using the sequence number of a data record in a shard. A sequence number is the identifier associated with every record ingested in the stream, and is assigned when a record is put into the stream. Each stream has one or more shards.

        You must specify the shard iterator type.
        When you read repeatedly from a stream, use a GetShardIterator request to get the first shard iterator for use in your first GetRecords request and for subsequent reads use the shard iterator returned by the GetRecords request in NextShardIterator. A new shard iterator is returned by every GetRecords request in NextShardIterator, which you use in the ShardIterator parameter of the next GetRecords request.

        Read about different ShardIteratorTypes in above mentioned link. In the GetRecords, you need to specify the sequence number and shard iterator type to read the record.

        Each consumer reads from a particular shard, using a shard iterator. A shard iterator represents the position in the stream from which the consumer will read. When they start reading from a stream, consumers get a shard iterator, which can be used to change where the consumers read from the stream. When the consumer performs a read operation, it receives a batch of data records based on the position specified by the shard iterator.

    Checkpointing
    -------------
        http://docs.aws.amazon.com/streams/latest/dev/learning-kinesis-module-one-consumer.html#learning-kinesis-module-one-consumer-supplement

        It is the process of tracking which messages have been successfully read from the stream.

        The term checkpointing means recording the point in the stream up to the data records that have been consumed and processed thus far,
        so that if the application crashes, the stream is read from that point and not from the beginning of the stream. The subject of checkpointing and the various design patterns and best practices for it are outside the scope of this chapter, but is something you may be confronted with in production environments.

    Batching
    --------
        http://docs.aws.amazon.com/streams/latest/dev/kinesis-kpl-concepts.html#w2ab1c12b7b7c19c11

        Batching refers to performing a single action on multiple items instead of repeatedly performing the action on each individual item.

        In this context, the "item" is a record, and the action is sending it to Kinesis Streams. In a non-batching situation, you would place each record in a separate Kinesis Streams record and make one HTTP request to send it to Kinesis Streams. With batching, each HTTP request can carry multiple records instead of just one.

        The KPL supports two types of batching:

            Aggregation – Storing multiple records within a single Kinesis Streams record.
            Collection – Using the API operation PutRecords to send multiple Kinesis Streams records to one or more shards in your Kinesis stream.

        The two types of KPL batching are designed to co-exist and can be turned on or off independently of one another. By default, both are turned on.

        Aggregation

            Aggregation refers to the storage of multiple records in a Kinesis Streams record. Aggregation allows customers to increase the number of records sent per API call, which effectively increases producer throughput.

            Kinesis Streams shards support up to 1,000 Kinesis Streams records per second, or 1 MB throughput. The Kinesis Streams records per second limit binds customers with records smaller than 1 KB. Record aggregation allows customers to combine multiple records into a single Kinesis Streams record. This allows customers to improve their per shard throughput.

            Consider the case of one shard in region us-east-1 that is currently running at a constant rate of 1,000 records per second, with records that are 512 bytes each. With KPL aggregation, you can pack one thousand records into only 10 Kinesis Streams records, reducing the RPS to 10 (at 50 KB each).

        Collection

            Collection refers to batching multiple Kinesis Streams records and sending them in a single HTTP request with a call to the API operation PutRecords, instead of sending each Kinesis Streams record in its own HTTP request.

            This increases throughput compared to using no collection because it reduces the overhead of making many separate HTTP requests. In fact, PutRecords itself was specifically designed for this purpose.

            Collection differs from aggregation in that it is working with groups of Kinesis Streams records. The Kinesis Streams records being collected can still contain multiple records from the user. The relationship can be visualized as such:

            record 0 --|
            record 1   |        [ Aggregation ]
                ...    |--> Amazon Kinesis record 0 --|
                ...    |                              |
            record A --|                              |
                                                      |
                ...                   ...             |
                                                      |
            record K --|                              |
            record L   |                              |      [ Collection ]
                ...    |--> Amazon Kinesis record C --|--> PutRecords Request
                ...    |                              |
            record S --|                              |
                                                      |
                ...                   ...             |
                                                      |
            record AA--|                              |
            record BB  |                              |
                ...    |--> Amazon Kinesis record M --|
                ...    |
            record ZZ--|

        Configuring the KPL

            http://docs.aws.amazon.com/streams/latest/dev/kinesis-kpl-config.html

            KinesisProducerConfiguration config = new KinesisProducerConfiguration()
                    .setRecordMaxBufferedTime(3000)
                    .setMaxConnections(1)
                    .setRequestTimeout(60000)
                    .setRegion("us-west-1");

            final KinesisProducer kinesisProducer = new KinesisProducer(config);

            You can also load a configuration from a properties file:
            KinesisProducerConfiguration config = KinesisProducerConfiguration.fromPropertiesFile("default_config.properties");

            default_config.properties
                RecordMaxBufferedTime = 100 - Maximum amount of itme (milliseconds) a record may spend being buffered before it gets sent. Records may be sent sooner than this depending on the other buffering limits.
                MaxConnections = 4 - Maximum number of connections to open to the backend. HTTP requests are sent in parallel over multiple connections. Setting this too high may impact latency and consume additional resources without increasing throughput.
                RequestTimeout = 6000
                Region = us-west-1

                # There should be normally no need to adjust this. If you want to limit the time records spend buffering, look into RecordMaxBufferedTime instead.
                AggregationEnabled = true - Enable aggregation. With aggregation, multiple user records are packed into a single KinesisRecord. If disabled, each user record is sent in its own KinesisRecord.
                AggregationMaxCount = 4294967295 - Maximum number of items to pack into an aggregated record.
                AggregationMaxSize = 51200 - Maximum number of bytes to pack into an aggregated Kinesis record.

                MertricsLevel = detailed - Controls the number of metrics that are uploaded to CloudWatch. "none" disables all metrics. "summary" enables the following metrics: UserRecordsPut, KinesisRecordsPut, ErrorsByCode, AllErrors, BufferingTime.
                MetricsNamespace = KinesisProducerLibrary

                # If true, throttled puts are not retried. The records that got throttled
                # will be failed immediately upon receiving the throttling error. This is
                # useful if you want to react immediately to any throttling without waiting
                # for the KPL to retry. For example, you can use a different hash key to send
                # the throttled record to a backup shard.
                #
                # If false, the KPL will automatically retry throttled puts. The KPL performs
                # backoff for shards that it has received throttling errors from, and will
                # avoid flooding them with retries. Note that records may fail from
                # expiration (see record_ttl) if they get delayed for too long because of
                # throttling.
                FailIfThrottled = false



            You can set max number of records that needs to be aggregated

            For more information about each property, see https://github.com/awslabs/amazon-kinesis-producer/blob/master/java/amazon-kinesis-producer-sample/default_config.properties

    Consumer De-aggregation
    -----------------------
        http://docs.aws.amazon.com/streams/latest/dev/kinesis-kpl-consumer-deaggregation.html

        If KPL aggregation is being used on the producer side, there is a subtlety involving checkpointing:
        all subrecords within an aggregated record have the same sequence number, so additional data has to be stored with the checkpoint if you need to distinguish between subrecords.
        This additional data is referred to as the subsequence number.

    Consuming Empty Records
    -----------------------
        https://aws.amazon.com/kinesis/streams/faqs/
        One possible reason is that there is no record at the position specified by the current shard iterator. This could happen even if you are using TRIM_HORIZON as shard iterator type. An Amazon Kinesis stream represents a continuous stream of data. You should call GetRecords operation in a loop and the record will be returned when the shard iterator advances to the position where the record is stored.
        
    Handling Duplicate Records
    --------------------------
        http://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-duplicates.html

        There are two primary reasons why records may be delivered more than one time to your Amazon Kinesis Streams application: producer retries and consumer retries. Your application must anticipate and appropriately handle processing individual records multiple times.
        
        Producer Retries
        
            Consider a producer that experiences a network-related timeout after it makes a call to PutRecord, but before it can receive an acknowledgement from Amazon Kinesis Streams. 
            The producer cannot be sure if the record was delivered to Kinesis Streams. 
            Assuming that every record is important to the application, the producer would have been written to retry the call with the same data. 
            If both PutRecord calls on that same data were successfully committed to Kinesis Streams, then there will be two Kinesis Streams records. 
            Although the two records have identical data, they also have unique sequence numbers. 
            Applications that need strict guarantees should embed a primary key within the record to remove duplicates later when processing. 
            
            Note that the number of duplicates due to producer retries is usually low compared to the number of duplicates due to consumer retries.
        
        Consumer Retries
        
            Consumer (data processing application) retries happen when record processors restart. Record processors for the same shard restart in the following cases:
        
                - A worker terminates unexpectedly
                - Worker instances are added or removed
                - Shards are merged or split
                - The application is deployed
        
        In all these cases, the shards-to-worker-to-record-processor mapping is continuously updated to load balance processing. 
        Shard processors that were migrated to other instances restart processing records from the last checkpoint. 
        This results in duplicated record processing as shown in the example below. 
        For more information about load-balancing, see Resharding, Scaling, and Parallel Processing.
        
        Example: 
        
            Consumer Retries Resulting in Redelivered Records
            
            In this example, you have an application that continuously reads records from a stream, aggregates records into a local file, and uploads the file to Amazon S3. For simplicity, assume there is only 1 shard and 1 worker processing the shard. Consider the following example sequence of events, assuming that the last checkpoint was at record number 10000:
            
                1. A worker reads the next batch of records from the shard, records 10001 to 20000.
                2. The worker then passes the batch of records to the associated record processor.
                3. The record processor aggregates the data, creates an Amazon S3 file, and uploads the file to Amazon S3 successfully.
                4. Worker terminates unexpectedly before a new checkpoint can occur.
                5. Application, worker, and record processor restart.
                6. Worker now begins reading from the last successful checkpoint, in this case 10001.
            
            Thus, records 10001-20000 are consumed more than one time.
        
        Being Resilient to Consumer Retries
        
            Even though records may be processed more than one time, your application may want to present the side effects as if records were processed only one time (idempotent processing). 
            Solutions to this problem vary in complexity and accuracy. If the destination of the final data can handle duplicates well, we recommend relying on the final destination to achieve idempotent processing. 
            For example, with Elasticsearch you can use a combination of versioning and unique IDs to prevent duplicated processing.
        
            In the example application in the previous section, it continuously reads records from a stream, aggregates records into a local file, and uploads the file to Amazon S3. As illustrated, records 10001 -20000 are consumed more than one time resulting in multiple Amazon S3 files with the same data. One way to mitigate duplicates from this example is to ensure that step 3 uses the following scheme:
            
                1. Record Processor uses a fixed number of records per Amazon S3 file, such as 5000.                
                2. The file name uses this schema: Amazon S3 prefix, shard-id, and First-Sequence-Num. In this case, it could be something like sample-shard000001-10001.               
                3. After you upload the Amazon S3 file, checkpoint by specifying Last-Sequence-Num. In this case, you would checkpoint at record number 15000.
            
            With this scheme, even if records are processed more than one time, the resulting Amazon S3 file has the same name and has the same data. The retries only result in writing the same data to the same file more than one time.
            
            In the case of a reshard operation, 
            the number of records left in the shard may be less than your desired fixed number needed. In this case, your shutdown() method has to flush the file to Amazon S3 and checkpoint on the last sequence number. The above scheme is compatible with reshard operations as well.

    Handling Startup, Shutdown and Throttling
    -----------------------------------------
        http://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-additional-considerations.html
        
        Starting Up Data Producers and Data Consumers
        
            By default, the KCL begins reading records from the tip of the stream;, which is the most recently added record. In this configuration, if a data-producing application adds records to the stream before any receiving record processors are running, the records are not read by the record processors after they start up.
            
            To change the behavior of the record processors so that it always reads data from the beginning of the stream, set the following value in the properties file for your Amazon Kinesis Streams application:
                    
            initialPositionInStream = TRIM_HORIZON
            Amazon Kinesis Streams keeps records for 24 to 168 hours. This time frame is called the retention period. Setting the starting position to the TRIM_HORIZON will start the record processor with the oldest data in the stream, as defined by the retention period. Even with the TRIM_HORIZON setting, if a record processor were to start after a greater time has passed than the retention period, then some of the records in the stream will no longer be available. For this reason, you should always have consumer applications reading from the stream and use the CloudWatch metric GetRecords.IteratorAgeMilliseconds to monitor that applications are keeping up with incoming data.
            
            In some scenarios, it may be fine for record processors to miss the first few records in the stream. For example, you might run some initial records through the stream to test that the stream is working end-to-end as expected. After doing this initial verification, you would then start your workers and begin to put production data into the stream.
            
            For more information about the TRIM_HORIZON setting, see Using Shard Iterators.
        
        Shutting Down an Amazon Kinesis Streams Application
        
            When your Amazon Kinesis Streams application has completed its intended task, you should shut it down by terminating the EC2 instances on which it is running. You can terminate the instances using the AWS Management Console or the AWS CLI.
            
            After shutting down your Amazon Kinesis Streams application, you should delete the Amazon DynamoDB table that the KCL used to track the application's state.
        
        Read Throttling
        
            The throughput of a stream is provisioned at the shard level. Each shard has a read throughput of up to 5 transactions per second for reads, up to a maximum total data read rate of 2 MB per second. If an application (or a group of applications operating on the same stream) attempts to get data from a shard at a faster rate, Kinesis Streams throttles the corresponding Get operations.
            
            In an Amazon Kinesis Streams application, if a record processor is processing data faster than the limit — such as in the case of a failover — throttling occurs. Because the Kinesis Client Library manages the interactions between the application and Kinesis Streams, throttling exceptions occur in the KCL code rather than in the application code. However, because the KCL logs these exceptions, you see them in the logs.
            
            If you find that your application is throttled consistently, you should consider increasing the number of shards for the stream.    
            
    Resharding, Scaling, and Parallel Processing
    --------------------------------------------
        Items don't move between shards. After re-sharding, new records are put into new shards, but old records are never transferred from the parent shard, and no more new records are added to the (now closed) parent shard. Data persists in the parent shard for its normal 24 hour lifespan even after it is closed. Your record processor would only be shutdown after it has reached the end of the data from the parent shard.

        http://docs.aws.amazon.com/streams/latest/dev/kinesis-using-sdk-java-resharding.html

            Kinesis Streams supports resharding, which enables you to adjust the number of shards in your stream in order to adapt to changes in the rate of data flow through the stream. Resharding is considered an advanced operation. If you are new to Kinesis Streams, return to this subject after you are familiar with all the other aspects of Kinesis Streams.

            There are two types of resharding operations: shard split and shard merge. In a shard split, you divide a single shard into two shards. In a shard merge, you combine two shards into a single shard. Resharding is always "pairwise" in the sense that you cannot split into more than two shards in a single operation, and you cannot merge more than two shards in a single operation. The shard or pair of shards that the resharding operation acts on are referred to as parent shards. The shard or pair of shards that result from the resharding operation are referred to as child shards.

            Splitting increases the number of shards in your stream and therefore increases the data capacity of the stream. Because you are charged on a per-shard basis, splitting increases the cost of your stream. Similarly, merging reduces the number of shards in your stream and therefore decreases the data capacity—and cost—of the stream.


        http://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-scaling.html

            Resharding is typically performed by an administrative application that monitors shard data-handling metrics. Although the KCL itself doesn't initiate resharding operations, it is designed to adapt to changes in the number of shards that result from resharding.

            As noted in Tracking Amazon Kinesis Streams Application State, the KCL tracks the shards in the stream using an Amazon DynamoDB table. When new shards are created as a result of resharding, the KCL discovers the new shards and populates new rows in the table. The workers automatically discover the new shards and create processors to handle the data from them. The KCL also distributes the shards in the stream across all the available workers and record processors.

            The KCL ensures that any data that existed in shards prior to the resharding is processed first. After that data has been processed, data from the new shards is sent to record processors. In this way, the KCL preserves the order in which data records were added to the stream for a particular partition key.


            Example: Resharding, Scaling, and Parallel Processing

            The following example illustrates how the KCL helps you handle scaling and resharding:
            •	For example, if your application is running on one EC2 instance, and is processing one Kinesis stream that has four shards. This one instance has one KCL worker and four record processors (one record processor for every shard). These four record processors run in parallel within the same process.
            •	Next, if you scale the application to use another instance, you have two instances processing one stream that has four shards. When the KCL worker starts up on the second instance, it load-balances with the first instance, so that each instance now processes two shards.
            •	If you then decide to split the four shards into five shards. The KCL again coordinates the processing across instances: one instance processes three shards, and the other processes two shards. A similar coordination occurs when you merge shards.

            Typically, when you use the KCL, you should ensure that the number of instances does not exceed the number of shards (except for failure standby purposes). Each shard is processed by exactly one KCL worker and has exactly one corresponding record processor, so you never need multiple instances to process one shard. However, one worker can process any number of shards, so it's fine if the number of shards exceeds the number of instances.

            To scale up processing in your application, you should test a combination of these approaches:
            •	Increasing the instance size (because all record processors run in parallel within a process)
            •	Increasing the number of instances up to the maximum number of open shards (because shards can be processed independently)
            •	Increasing the number of shards (which increases the level of parallelism)

            Note that you can use Auto Scaling to automatically scale your instances based on appropriate metrics. For more information, see the Auto Scaling User Guide.

            When resharding increases the number of shards in the stream, the corresponding increase in the number of record processors increases the load on the EC2 instances that are hosting them. If the instances are part of an Auto Scaling group, and the load increases sufficiently, the Auto Scaling group adds more instances to handle the increased load. You should configure your instances to launch your Amazon Kinesis Streams application at startup, so that additional workers and record processors become active on the new instance right away.

    Changing the Data Retention Period
    ----------------------------------
        http://docs.aws.amazon.com/streams/latest/dev/kinesis-extended-retention.html

        An Kinesis stream stores records from 24 hours by default, up to 168 hours (7 days).
        Kinesis Streams stops making records inaccessible at the old retention period within several minutes of increasing the retention period. For example, changing the retention period from 24 hours to 48 hours means records added to the stream 23 hours 55 minutes prior will still be available after 24 hours have passed.

        Kinesis Streams almost immediately makes records older than the new retention period inaccessible upon decreasing the retention period. Therefore, great care should be taken.

    Using Server-Side Encryption
    ----------------------------
        http://docs.aws.amazon.com/streams/latest/dev/server-side-encryption.html

        Server-side encryption is a feature in Amazon Kinesis Streams that automatically encrypts data before it's at rest by using an AWS KMS customer master key (CMK) you specify. Data is encrypted before it's written to the Kinesis stream storage layer, and decrypted after it’s retrieved from storage. As a result, your data is encrypted at rest within the Kinesis Streams service.

    Troubleshooting
    ---------------
        http://docs.aws.amazon.com/streams/latest/dev/troubleshooting-consumers.html

    CloudWatch Metrics
    ------------------
        http://docs.aws.amazon.com/streams/latest/dev/monitoring-with-cloudwatch.html

        Kinesis Streams Service Metric
            http://docs.aws.amazon.com/streams/latest/dev/monitoring-with-cloudwatch.html
        KPL metric
            http://docs.aws.amazon.com/streams/latest/dev/monitoring-with-kpl.html
        KCL Metric
            http://docs.aws.amazon.com/streams/latest/dev/monitoring-with-kcl.html
        Agent Metric
            http://docs.aws.amazon.com/streams/latest/dev/agent-health.html

    CloudTrail Logging
    ------------------
        http://docs.aws.amazon.com/streams/latest/dev/logging-using-cloudtrail.html

        Logging Amazon Kinesis Streams API Calls Using AWS CloudTrail.
        Amazon Kinesis Streams is integrated with AWS CloudTrail, which captures API calls made by or on behalf of Kinesis Streams and delivers the log files to the Amazon S3 bucket that you specify.

    Tagging Your Streams
    --------------------
        http://docs.aws.amazon.com/streams/latest/dev/tagging.html

        You can assign your own metadata to streams you create in Amazon Kinesis Streams in the form of tags. A tag is a key-value pair that you define for a stream. Using tags is a simple yet powerful way to manage AWS resources and organize data, including billing data.