package com.serbriss.supercards.exception

class UserNotFoundException(id: Long) : RuntimeException("User $id not found") {
}