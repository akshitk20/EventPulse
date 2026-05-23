package com.eventpulse.domain.event;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class EventStatusConverter implements AttributeConverter<EventStatus, String> {

    @Override
    public String convertToDatabaseColumn(EventStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public EventStatus convertToEntityAttribute(String value) {
        return value == null ? null : EventStatus.valueOf(value.toUpperCase());
    }
}
