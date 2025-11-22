package com.dunnhumby;

import java.time.Duration;
import java.util.Properties;

import com.dunnhumby.model.Event;
import com.dunnhumby.model.SessionSummary;
import com.dunnhumby.processing.SessionProcessor;
import com.dunnhumby.utils.EventDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public CommandLineRunner flinkRunner() {
        return args -> {
            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            Properties kinesisConsumerConfig = new Properties();
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.AWS_REGION, "us-east-1");
            kinesisConsumerConfig.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

            DataStream<Event> eventStream =
                    env.addSource(new FlinkKinesisConsumer<>("input-event-stream", new EventDeserializer(), kinesisConsumerConfig))
                            .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                    .withTimestampAssigner((event, ts) -> event.getEventTime()));

            DataStream<SessionSummary> sessionStream = eventStream.keyBy(Event::getUserId).process(new SessionProcessor());

            // TODO: Real-Time Writes from Flink to Scylla
            sessionStream.print();

            env.execute("Real-Time Event Processor");
        };
    }
}
