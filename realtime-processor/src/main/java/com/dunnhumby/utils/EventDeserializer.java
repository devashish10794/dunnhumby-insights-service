package com.dunnhumby.utils;

import java.io.IOException;

import com.dunnhumby.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;

public class EventDeserializer extends AbstractDeserializationSchema<Event> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Event deserialize(byte[] bytes) throws IOException {
        return mapper.readValue(bytes, Event.class);
    }
}
