package com.stagepass.booking.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID eventId,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull Item> items,
        @NotBlank @Size(max = 120) String attendeeName,
        @NotBlank @Email @Size(max = 254) String attendeeEmail,
        @Pattern(regexp = Formats.MOBILE, message = "must be a 10-digit Indian mobile number") String attendeePhone,
        @Size(max = 60) String buyerState) {

    public record Item(@NotNull UUID ticketTypeId, @Min(1) @Max(100) int quantity) {
    }
}
