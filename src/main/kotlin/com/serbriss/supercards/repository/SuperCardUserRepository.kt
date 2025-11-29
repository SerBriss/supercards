package com.serbriss.supercards.repository

import com.serbriss.supercards.repository.entity.SuperCardUser
import org.springframework.data.repository.CrudRepository

interface SuperCardUserRepository : CrudRepository<SuperCardUser, Long>