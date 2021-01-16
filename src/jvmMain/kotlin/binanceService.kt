import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.codec.binary.Hex
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */

const val MAX_ORDERS_PER_BATCH = 5

// Spot
//const val wsBaseUrl = "wss://stream.binance.com/stream"
//val defaultTickers = listOf("BTCUSDT", "ETHUSDT", "ETHBTC", "LTCBTC", "DOGEBTC", "LINKBTC")
//val defaultTickers = listOf("BTCUSDT", "ETHUSDT", "LTCUSDT")

const val baseUrl = "https://testnet.binancefuture.com/fapi/v1"

// Futs USDT
const val wsBaseUrl = "wss://fstream.binance.com/stream"
val dotenv = dotenv()
val apiKey = dotenv["API_KEY"]
val apiSecret = dotenv["API_SECRET"]
val defaultTickers = listOf("BTCUSDT")

// Futs coin
//const val wsBaseUrl = "wss://dstream.binance.com/stream"
//val defaultTickers = listOf("BTCUSD_PERP")

@Serializable
data class BinanceSubscription(
    @Required val method: String = "SUBSCRIBE",
    @Required val params: List<String> = listOf(),
    @Required val id: Int = 1
)

// Types of streams
@Serializable
data class MiniTicker(
    @SerialName("s") val symbol: String,
    @SerialName("c") val value: String
)

@Serializable
data class AggTrade(
    @SerialName("s") val symbol: String,
    @SerialName("p") val price: Double,
    @SerialName("q") val quantity: Double,
    @SerialName("m") val buyerIsMM: Boolean
)

@Serializable
data class ForceOrder(
    @SerialName("s") val symbol: String,
    @SerialName("S") val side: String,
    @SerialName("q") val originalQuantity: Double,
    @SerialName("ap") val averagePrice: Double,
    @SerialName("X") val orderStatus: String
)

@Serializable
data class KLine(
    @SerialName("s") val symbol: String,
    @SerialName("t") val startTime: String, // TODO convert to datetime ?
    @SerialName("T") val closeTime: String, // TODO convert to datetime ?
    @SerialName("i") val interval: String,
    @SerialName("o") val open: Double,
    @SerialName("h") val high: Double,
    @SerialName("l") val low: Double,
    @SerialName("c") val close: Double,
    @SerialName("v") val volume: Double
)

// Orders
enum class OrderSide { BUY, SELL }

enum class OrderType { LIMIT, MARKET }

enum class OrderTimeInForce {
    GTC, // Good Till Cancel
    IOC, // Immediate or Cancel
    FOK, // Fill or Kill
    GTX  // Good Till Crossing (Post Only)
}

@Serializable
data class OrderRequest(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: String,
    val price: String,
    val timeInForce: OrderTimeInForce = OrderTimeInForce.GTX)
// TODO add reduceOnly

// misc
val miniTickers = mutableMapOf<String, MiniTicker>()

val format = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

fun signRequest(data: String): String {
    return createSignature(data, apiSecret)
}

fun createSignature(data: String, key: String): String {
    val sha256Hmac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
    sha256Hmac.init(secretKey)

    return Hex.encodeHexString(sha256Hmac.doFinal(data.toByteArray()))

    // For base64
    // return Base64.getEncoder().encodeToString(sha256Hmac.doFinal(data.toByteArray()))
}

fun extractQueryParamsAsText(sourceBuilder: URLBuilder): String {
    val builderCopy = URLBuilder(sourceBuilder)
    return builderCopy
        .build()
        .fullPath
        .substringAfter("?")
}

fun URLBuilder.signedParameters(block: URLBuilder.() -> Unit) {
    val currentBuilder = this
    currentBuilder.block()
    val queryParamsAsText = extractQueryParamsAsText(currentBuilder)
    parameters.append("signature", signRequest(queryParamsAsText))
}

suspend fun createOrder(orderRequest: OrderRequest): HttpResponse {
    return client.post("$baseUrl/order") {
        header("X-MBX-APIKEY", apiKey)
        url {
            signedParameters {
                parameter("symbol", orderRequest.symbol)
                parameter("side", orderRequest.side)
                parameter("type", orderRequest.type)
                parameter("quantity", orderRequest.quantity)
                parameter("price", orderRequest.price)
                parameter("timeInForce", orderRequest.timeInForce)
                parameter("recvWindow", 5000)
                parameter("timestamp", Instant.now().toEpochMilli())
            }
        }
    }
}

suspend fun batchOrders(orderRequests: List<OrderRequest>): HttpResponse {
    val batchOrdersParamValue = format.encodeToString(orderRequests)
    println(batchOrdersParamValue)

    return client.post("$baseUrl/batchOrders") {
        header("X-MBX-APIKEY", apiKey)
        url {
            signedParameters {
                parameter("batchOrders", batchOrdersParamValue)
                parameter("recvWindow", 5000)
                parameter("timestamp", Instant.now().toEpochMilli())
            }
        }
    }

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
                .plus(defaultTickers.map { symbol -> "${symbol.toLowerCase()}@aggTrade" }
                    .plus(defaultTickers.map { symbol -> "${symbol.toLowerCase()}@forceOrder" }
                        .plus(defaultTickers.map { symbol -> "${symbol.toLowerCase()}@kline_1m" })))
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
                    val data = binanceElement.jsonObject["data"]!!
                    when (val it = stream.jsonPrimitive.content.split('@').last()) {
                        "miniTicker" -> {
                            val miniTicker = format.decodeFromJsonElement<MiniTicker>(data)
                            //println("[${miniTicker.symbol}] ${miniTicker.value}")
                            miniTickers[miniTicker.symbol] = miniTicker
                        }
                        "aggTrade" -> {
                            val aggTrade = format.decodeFromJsonElement<AggTrade>(data)
                            // TODO contract on futs coin is 100usd for btc pair and 10usd for other pairs
                            // TODO contract on futs usdt has the amount of coin as quantity
                            // TODO spot has the amount of coin as quantity
                            if (aggTrade.quantity * aggTrade.price >= 100_000) {
                                println(
                                    "[${aggTrade.symbol}] " +
                                            "${(aggTrade.quantity * aggTrade.price / 1000).roundToInt()}K " +
                                            if (aggTrade.buyerIsMM) "SELL" else "BUY"
                                )
                            }
                        }
                        "forceOrder" -> {
                            val forceOrder = format.decodeFromJsonElement<ForceOrder>(data.jsonObject["o"]!!)
                            // TODO probably same remark as aggTrade for quantities
                            println(
                                "[${forceOrder.symbol}] " +
                                        "${(forceOrder.originalQuantity * forceOrder.averagePrice).roundToInt()} " +
                                        "LIQUIDATION ${forceOrder.side} (${forceOrder.orderStatus})"
                            )
                        }
                        "kline_1m" -> {
                            val kLine = format.decodeFromJsonElement<KLine>(data.jsonObject["k"]!!)
                            val date = LocalDateTime.ofEpochSecond(
                                kLine.startTime.substring(0, kLine.startTime.length - 3).toLong(), 0, ZoneOffset.UTC
                            )
                            println(
                                "[${kLine.symbol}] " +
                                        "KLINE TS $date " +
                                        "OHLC(${kLine.open.roundToInt()} ${kLine.high.roundToInt()} " +
                                        "${kLine.low.roundToInt()} ${kLine.close.roundToInt()}) " +
                                        "vol ${kLine.volume}"
                            )
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
