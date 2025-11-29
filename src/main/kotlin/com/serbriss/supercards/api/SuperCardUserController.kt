package com.serbriss.supercards.api

import com.serbriss.supercards.api.dto.SuperCardUserRequest
import com.serbriss.supercards.api.dto.SuperCardUserResponse
import com.serbriss.supercards.exception.UserNotFoundException
import com.serbriss.supercards.repository.SuperCardUserRepository
import com.serbriss.supercards.repository.entity.SuperCardUser
import com.serbriss.supercards.util.userToResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class SuperCardUserController(private val repository: SuperCardUserRepository) {

    @GetMapping
    fun getAllUsers(): List<SuperCardUserResponse> {
        return repository.findAll().map { it.userToResponse() }
    }

    @PostMapping
    fun createUser(@RequestBody userRequest: SuperCardUserRequest): ResponseEntity<SuperCardUserResponse> {
        val createdUser = repository.save(
            SuperCardUser(
                username = userRequest.username,
                email = userRequest.email
            )
        )
        return ResponseEntity.ok(createdUser.userToResponse())
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<SuperCardUserResponse> {
        val user = repository.findById(id).orElseThrow { UserNotFoundException(id) }
        return ResponseEntity.ok(user.userToResponse())
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody userRequest: SuperCardUserRequest
    ): ResponseEntity<SuperCardUserResponse> {
        val existingUser = repository.findById(id).orElseThrow { UserNotFoundException(id) }
        val updatedUser = existingUser.copy(username = userRequest.username, email = userRequest.email)
        val savedUser = repository.save(updatedUser)
        return ResponseEntity.ok(savedUser.userToResponse())
    }

    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: Long): ResponseEntity<Void> {
        if (!repository.existsById(id)) {
            throw UserNotFoundException(id)
        }
        repository.deleteById(id)
        return ResponseEntity.ok().build()
    }
}