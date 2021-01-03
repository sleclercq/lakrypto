import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */
@ExperimentalTime
fun main() {
    println("Hello World!")
    runBlocking {
        performWebSocket()
        delay(Duration.INFINITE)
    }
}
