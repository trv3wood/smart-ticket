package com.example.ticket.api;

import com.example.ticket.domain.*;
import jakarta.validation.constraints.*;

public final class Requests {
    private Requests() {}
    public record Create(@NotBlank @Size(max=120) String title,
                         @NotBlank @Size(max=4000) String description,
                         @NotNull Category category, @NotNull Priority priority) {}
    public record Transition(@NotNull Status target, @NotNull @Min(0) Integer version,
                             @NotBlank @Size(max=500) String note) {}
}
