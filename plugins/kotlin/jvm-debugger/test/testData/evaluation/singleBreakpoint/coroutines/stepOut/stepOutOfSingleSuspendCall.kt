// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3)-javaagent

import kotlinx.coroutines.*

fun main() {
    runBlocking  {
        for (i in 0..100) {
            launch {
                val x = funWithSuspendLast(i)
                // EXPRESSION: i
                // RESULT: 25: I
                println("x = $x")
                delay(1)
                println("i = $i")
            }
        }
    }
}

suspend fun funWithSuspendLast(i: Int): Int {
    if (i == 25) {
        //Breakpoint!
        return someInt()
    } else {
        return 10
    }
}

suspend fun someInt(): Int {
    delay(1)
    return 42
}

// STEP_OUT: 1
// STEP_OVER: 3

// REGISTRY: debugger.filter.breakpoints.by.coroutine.id=true
