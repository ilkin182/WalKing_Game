package com.example.domain.model

data class GridCell(
    val address: String,
    val corners: List<Coordinate>,
    val isStomped: Boolean
)
