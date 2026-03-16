package com.serbriss.supercards.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class SuperCardUserRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank @field:Email
    val email: String,
)
