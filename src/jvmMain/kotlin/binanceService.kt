import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */
// Spot
//const val wsBaseUrl = "wss://stream.binance.com/stream"
//val defaultTickers = listOf("BTCUSDT", "ETHUSDT", "ETHBTC", "LTCBTC", "DOGEBTC", "LINKBTC")
//val defaultTickers = listOf("BTCUSDT", "ETHUSDT", "LTCUSDT")

// Futs USDT
const val wsBaseUrl = "wss://fstream.binance.com/stream"
val defaultTickers = listOf("BTCUSDT")

// Futs coin
//const val wsBaseUrl = "wss://dstream.binance.com/stream"
//val defaultTickers = listOf("BTCUSD_PERP")

@Serializable
data class BinanceSubscription(@Required val method: String = "SUBSCRIBE",
                               @Required val params: List<String> = listOf(),
                               @Required val id: Int = 1)

@Serializable
data class MiniTicker(@SerialName("s") val symbol: String,
                      @SerialName("c") val value: String)

/*{
    "e": "aggTrade",  // Event type
    "E": 123456789,   // Event time
    "s": "BNBBTC",    // Symbol
    "a": 12345,       // Aggregate trade ID
    "p": "0.001",     // Price
    "q": "100",       // Quantity
    "f": 100,         // First trade ID
    "l": 105,         // Last trade ID
    "T": 123456785,   // Trade time
    "m": true,        // Is the buyer the market maker?
    "M": true         // Ignore
}*/
@Serializable
data class AggTrade(@SerialName("s") val symbol: String,
                    @SerialName("p") val price: Double,
                    @SerialName("q") val quantity: Double,
                    @SerialName("m") val buyerIsMM: Boolean)

val miniTickers = mutableMapOf<String, MiniTicker>()

val format = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

val client = HttpClient {
    install(WebSockets)
}

suspend fun performWebSocket() {
    println("Connecting...")
    client.wss(
        urlString = wsBaseUrl
    ) {
        println("Connected!")

        val binanceSubscription = BinanceSubscription(
            //params = listOf("!miniTicker@arr")
            params = defaultTickers.map { symbol -> "${symbol.toLowerCase()}@miniTicker" }
                .plus(defaultTickers.map { symbol -> "${symbol.toLowerCase()}@aggTrade" })
        )
        //println(format.encodeToString(binanceSubscription))
        send(format.encodeToString(binanceSubscription))

        try {
            for (frame in incoming) {
                val text = (frame as Frame.Text).readText()
                val binanceElement = Json.parseToJsonElement(text)
                val stream = binanceElement.jsonObject["stream"]
                if (stream == null) {
                    //println(binanceElement)
                } else {
                    /*
                   val data = binanceElement.jsonObject["data"]
                   println(data)
                   */
                   when (val it = stream.jsonPrimitive.content.split('@').last()) {
                       "miniTicker" -> {
                           val data = binanceElement.jsonObject["data"]
                           val miniTicker = format.decodeFromJsonElement<MiniTicker>(data!!)
                           //println("[${miniTicker.symbol}] ${miniTicker.value}")
                           miniTickers[miniTicker.symbol] = miniTicker
                       }
                       "aggTrade" -> {
                           val data = binanceElement.jsonObject["data"]
                           val aggTrade = format.decodeFromJsonElement<AggTrade>(data!!)
                           // TODO contract on futs coin is 100usd for btc pair and 10usd for other pairs
                           // TODO contract on futs usdt has the amount of coin as quantity
                           // TODO spot has the amount of coin as quantity
                           if (aggTrade.quantity * aggTrade.price >= 100_000) {
                               println("[${aggTrade.symbol}] " +
                                       "${aggTrade.quantity * aggTrade.price} " +
                                       if (aggTrade.buyerIsMM) "SELL" else "BUY")
                           }
                       }
                       else -> println("Could not decode $it")
                   }

                }

            }
        } catch (e: ClosedReceiveChannelException) {
            e.printStackTrace()
            println("onClose ${closeReason.await()}")
        } catch (e: Throwable) {
            e.printStackTrace()
            println("onError ${closeReason.await()}")
        }

    }

}