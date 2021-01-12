import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Hex
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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


    embeddedServer(Netty, port = 8000) {
        routing {
            get ("/") {
                log.info("Hello from /api/v1!")
                call.respondText(clientTest())
            }
        }
    }.start(wait = true)


}

