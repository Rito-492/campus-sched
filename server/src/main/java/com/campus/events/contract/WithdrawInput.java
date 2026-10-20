package com.campus.events.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawInput(@NotBlank @Size(max = 1000) String reason) {}
