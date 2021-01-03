import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */

const val wsBaseUrl = "wss://stream.binance.com/stream"
val defaultTickers = listOf("BTCUSDT", "ETHUSDT", "ETHBTC", "LTCBTC", "DOGEBTC", "LINKBTC")

data class Message(val method: String, val params: List<String>, val id: Int)

enum class ConnectionStatus { DISCONNECTED, CONNECTED }

@Serializable
data class BinanceSubscription(@Required val method: String = "SUBSCRIBE",
                               @Required val params: List<String> = listOf(),
                               @Required val id: Int = 1)

//{"stream":"linkbtc@miniTicker","data":{"e":"24hrMiniTicker","E":1609635173540,"s":"LINKBTC","c":"0.00037512",
//"o":"0.00039578","h":"0.00040736","l":"0.00035792","v":"2850757.80000000","q":"1080.30113006"}}
@Serializable
data class MiniTicker(val stream: String, val data: MiniTickerData)

@Serializable
data class MiniTickerData(val e: String, val s: String, val c: String, val o: String,
val h: String, val l: String, val v: String, val q: String)

val client = HttpClient {
    install(WebSockets)
}

fun performWebSocket() {
    GlobalScope.launch {
        println("Connecting...")
        client.wss(
            urlString = wsBaseUrl
        ) {
            println("Connected!")

            val binanceSubscription = BinanceSubscription(
                params = defaultTickers.map { symbol -> "${symbol.toLowerCase()}@miniTicker" }
            )
            println(Json.encodeToString(binanceSubscription))
            send(Json.encodeToString(binanceSubscription))

            try {
                for (frame in incoming) {
                    val text = (frame as Frame.Text).readText()
                    println(text)
                    //val obj = Json.decodeFromString<MiniTicker>(text)
                    //println(obj)
                }
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }

        }
    }
}
