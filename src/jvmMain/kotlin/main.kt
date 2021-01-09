import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime


/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */
@ExperimentalTime
fun main() {
    println("Lakrypto starting...")

    GlobalScope.launch {
        repeat(Int.MAX_VALUE) {
            println(miniTickers.map {
                    entry -> "[${entry.key}] ${entry.value.value}"
            }.joinToString())
            delay(10000L)
        }
    }

    GlobalScope.launch {
        performWebSocket()
    }

    var input = readLine()
    while (input != "q") {
        println(input)
        input = readLine()
    }
}
