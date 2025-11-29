package com.serbriss.supercards.util

import com.serbriss.supercards.api.dto.SuperCardUserResponse
import com.serbriss.supercards.repository.entity.SuperCardUser

fun SuperCardUser.userToResponse() = SuperCardUserResponse(
    id = requireNotNull(id),
    username = username,
    email = email
)
