package com.example.metadatasurvey
import kotlin.math.floor

fun binDbTime(x: Int): Int {
    return when {
        x == 0 -> 0
        x < 5 -> 1
        x < 10 -> 2
        else -> 3
    }
}

fun binAge(age: Int): Int {
    if(age<5) {
        return 0
    }

    return floor(((age) / 16.0)).toInt()
}
