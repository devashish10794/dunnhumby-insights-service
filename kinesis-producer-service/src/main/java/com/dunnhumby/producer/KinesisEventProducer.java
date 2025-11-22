package com.dunnhumby.producer;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

@Service
public class KinesisEventProducer {
    private final KinesisAsyncClient kinesisClient;

    public KinesisEventProducer() {
        this.kinesisClient = KinesisAsyncClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    public CompletableFuture<Void> sendEvent(String streamName, String partitionKey, String data) {
        PutRecordRequest request =
                PutRecordRequest.builder().streamName(streamName).partitionKey(partitionKey).data(SdkBytes.fromUtf8String(data)).build();

        return kinesisClient.putRecord(request).thenAccept(response -> System.out.println("Event sent. Sequence: " + response.sequenceNumber()));
    }
}
