package com.dunnhumby.processing;

import com.dunnhumby.model.Event;
import com.dunnhumby.model.SessionSummary;
import lombok.RequiredArgsConstructor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.springframework.stereotype.Component;

@Component("prototype")
@RequiredArgsConstructor
public class SessionProcessor extends KeyedProcessFunction<String, Event, SessionSummary> {
    private transient ValueState<Long> sessionStart;
    private transient ValueState<Integer> viewCount;
    private transient ValueState<Integer> clickCount;
    private transient ValueState<Long> timer;

    private static final long SESSION_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    @Override
    public void open(Configuration parameters) {
        sessionStart = getRuntimeContext().getState(new ValueStateDescriptor<>("sessionStart", Long.class));
        viewCount = getRuntimeContext().getState(new ValueStateDescriptor<>("viewCount", Integer.class));
        clickCount = getRuntimeContext().getState(new ValueStateDescriptor<>("clickCount", Integer.class));
        timer = getRuntimeContext().getState(new ValueStateDescriptor<>("timer", Long.class));
    }

    @Override
    public void processElement(Event event, Context context, Collector<SessionSummary> out) throws Exception {
        if (sessionStart.value() == null) {
            sessionStart.update(event.getEventTime());
        }

        // Count events
        if ("view".equalsIgnoreCase(event.getEventType())) {
            viewCount.update((viewCount.value() == null ? 0 : viewCount.value()) + 1);
        } else if ("click".equalsIgnoreCase(event.getEventType())) {
            clickCount.update((clickCount.value() == null ? 0 : clickCount.value()) + 1);
        }

        // Set/reset session timeout
        long currentTimer = context.timestamp() + SESSION_TIMEOUT;
        context.timerService().deleteProcessingTimeTimer(timer.value() == null ? 0 : timer.value());
        context.timerService().registerProcessingTimeTimer(currentTimer);
        timer.update(currentTimer);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<SessionSummary> out) throws Exception {
        out.collect(new SessionSummary(ctx.getCurrentKey(), sessionStart.value(), timestamp, viewCount.value() == null ? 0 : viewCount.value(),
                clickCount.value() == null ? 0 : clickCount.value()));

        sessionStart.clear();
        viewCount.clear();
        clickCount.clear();
        timer.clear();
    }
}
