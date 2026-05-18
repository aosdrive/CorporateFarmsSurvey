package com.gop.survey.corporatefarm.data.remote.post

data class Floors(
    var FloorNumber: Int = 0,
    var Partitions: List<Partition> = emptyList()
)
