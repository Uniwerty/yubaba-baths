package ru.yubaba.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.yubaba.data.enums.PaymentMethod;
import ru.yubaba.data.enums.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class Requests {

    public record Login(@NotBlank @Size(max = 100) String login,
                        @NotBlank @Size(max = 128) String password) {
    }

    public record ClientInput(@NotBlank @Size(max = 255) String name,
                              @NotBlank @Size(max = 255) String contact,
                              @Size(max = 255) String notes,
                              Long version) {
    }

    public record LineInput(@NotNull Long ingredientId,
                            @NotNull @DecimalMin("0.001") @Digits(integer = 8, fraction = 3) BigDecimal quantity) {
    }

    public record Composition(@NotBlank @Size(max = 255) String name,
                              @NotBlank @Size(max = 255) String bathType,
                              Long preferredRoomId,
                              @Min(1) @Max(20) int attendants,
                              @Min(1) @Max(480) int durationMinutes,
                              @Min(1) @Max(120) int preparationMinutes,
                              @Min(0) @Max(120) int breakMinutes,
                              @Min(20) @Max(100) int temperature,
                              @NotBlank @Size(max = 4000) String steps,
                              @NotNull @Size(max = 2000) String extraServices,
                              @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
                              @NotNull @Size(max = 50) List<@Valid LineInput> lines) {
    }

    public record TemplateInput(@NotNull @Valid Composition composition, Long version) {
    }

    public record OrderInput(@NotNull Long clientId,
                             Long templateId,
                             @Min(1) @Max(100) int visitors,
                             @Min(0) @Max(10) int priority,
                             @NotNull @Valid Composition composition,
                             Long version) {
    }

    public record VersionInput(@NotNull Long version) {
    }

    public record CancelInput(@NotNull Long version, @NotBlank @Size(max = 1000) String reason) {
    }

    public record PayInput(@NotNull Long version, @NotNull PaymentMethod method) {
    }

    public record AccountInput(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}") String login,
                               @NotBlank @Size(min = 10, max = 72) String password,
                               @NotBlank @Size(max = 255) String name,
                               @NotNull Role role) {
    }

    public record BlockInput(boolean blocked) {
    }

    public record IngredientInput(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 255) String unit,
                                  @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 3) BigDecimal stock,
                                  @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 3) BigDecimal threshold,
                                  @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price) {
    }

    public record RoomInput(@NotBlank @Size(max = 255) String name,
                            @NotBlank @Size(max = 255) String bathType,
                            @Min(1) @Max(100) int capacity) {
    }

    public record ReportFilter(LocalDate from,
                               LocalDate to,
                               Long templateId,
                               PaymentMethod method,
                               @Min(1) @Max(100) Integer visitors) {
    }

    public record ReportInput(@NotBlank @Size(max = 255) String name,
                              @NotNull @Valid ReportFilter parameters) {
    }
}
