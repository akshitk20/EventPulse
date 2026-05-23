package com.eventpulse.domain.engagement;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class EngagementActionConverter implements AttributeConverter<EngagementAction, String> {

    @Override
    public String convertToDatabaseColumn(EngagementAction action) {
        return action == null ? null : action.name().toLowerCase();
    }

    @Override
    public EngagementAction convertToEntityAttribute(String value) {
        return value == null ? null : EngagementAction.valueOf(value.toUpperCase());
    }
}
