package com.stagepass.booking.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stagepass.booking.api.CreateBookingRequest.Item;

import java.util.List;
import java.util.UUID;

public record QuoteRequest(@NotNull UUID eventId, @NotEmpty @Size(max = 20) List<@Valid @NotNull Item> items) {
}
