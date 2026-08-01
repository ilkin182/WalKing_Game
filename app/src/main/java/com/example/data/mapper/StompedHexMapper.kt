package com.example.data.mapper

import com.example.data.local.entity.StompedHexEntity
import com.example.domain.model.StompedHex

fun StompedHexEntity.toDomain(): StompedHex = StompedHex(
    hexAddress = hexAddress,
    neighborhood = neighborhood,
    timestamp = timestamp,
    explorationLevel = explorationLevel
)

fun StompedHex.toEntity(): StompedHexEntity = StompedHexEntity(
    hexAddress = hexAddress,
    neighborhood = neighborhood,
    timestamp = timestamp,
    explorationLevel = explorationLevel
)
