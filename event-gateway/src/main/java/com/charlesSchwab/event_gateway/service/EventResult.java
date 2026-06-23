package com.charlesSchwab.event_gateway.service;

import com.charlesSchwab.event_gateway.entity.EventRecord;

public record EventResult(EventRecord event, boolean created) {}