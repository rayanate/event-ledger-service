package com.charlesSchwab.event_gateway.exception;

public class EventNotFoundException extends RuntimeException {
    private final String eventId;

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}
