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
import kotlinx.serialization.json.*
import mu.KotlinLogging
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

private val logger = KotlinLogging.logger {}

class BybitService {
    companion object {
        const val MAX_ORDERS_PER_BATCH = 5
        const val baseUrl = "https://api-testnet.bybit.com"

        const val wsBaseUrl = "wss://stream-testnet.bybit.com/realtime"
        val dotenv = dotenv()
        val apiKey = dotenv["BYBIT_API_KEY"]
        val apiSecret = dotenv["BYBIT_API_SECRET"]
        val defaultTickers = listOf("BTCUSD")

        @Serializable
        data class BybitSubscription(
            @Required val op: String = "subscribe",
            @Required val args: List<String> = listOf(),
        )

        enum class TradeSide { Buy, Sell }
        enum class TradeTickDirection { PlusTick, ZeroPlusTick, MinusTick, ZeroMinusTick }

        @Serializable
        data class Trade(
            @SerialName("trade_time_ms") val tradeTimeMs: Long,
            @SerialName("timestamp") val timestamp: String,
            @SerialName("symbol") val symbol: String,
            @SerialName("side") val side: TradeSide,
            @SerialName("size") val size: Int,
            @SerialName("price") val price: Double,
            @SerialName("tick_direction") val tickDirection : TradeTickDirection,
            @SerialName("trade_id") val tradeId: String
        )

        @Serializable
        data class KLine(
            @SerialName("start") val start: Long,
            @SerialName("end") val end: Long,
            @SerialName("open") val open: Double,
            @SerialName("high") val high: Double,
            @SerialName("low") val low: Double,
            @SerialName("close") val close: Double,
            @SerialName("volume") val volume: Long
        )

        val format = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        }


        val client = HttpClient {
            install(WebSockets)
        }

        suspend fun performWebSocket() {
            logger.info { "Connecting..." }
            client.wss(
                urlString = wsBaseUrl
            ) {
                logger.info { "Connected!" }

                val bybitSubscription = BybitSubscription(
                    //params = listOf("!miniTicker@arr")
                    args = listOf(defaultTickers.joinToString("|", "trade.")
                        { symbol -> symbol.toUpperCase() },
                        defaultTickers.joinToString("|", "klineV2.1.")
                        { symbol -> symbol.toUpperCase() }
                    )


                )
                //logger.info { format.encodeToString(bybitSubscription) }
                send(format.encodeToString(bybitSubscription))

                try {
                    for (frame in incoming) {
                        val text = (frame as Frame.Text).readText()
                        val bybitElement = Json.parseToJsonElement(text)
                        //logger.info { bybitElement }
                        val topic = bybitElement.jsonObject["topic"]
                        if (topic == null) {
                            //logger.info { bybitElement }
                        } else {
                            val data = bybitElement.jsonObject["data"]!!
                            when (val it = topic.jsonPrimitive.content.split('.').first()) {
                                "trade" -> {
                                    data.jsonArray.forEach {
                                        val trade = format.decodeFromJsonElement<Trade>(it)
                                        //if (trade.quantity * trade.price >= 100_000) {
                                        logger.info { "[${trade.symbol}] ${trade.size} ${trade.side}" }
                                        //}
                                    }
                                }
                                "klineV2" -> {
                                    /*
                                        actually reads as klineV2.1.BTCUSD so in order to handle multiple timeframes
                                        use the second part of the topic as timeframe
                                     */
                                    val symbol = topic.jsonPrimitive.content.split('.').last()
                                    data.jsonArray.forEach {
                                        val kLine = format.decodeFromJsonElement<KLine>(it)
                                        val date = LocalDateTime.ofEpochSecond(kLine.start, 0, ZoneOffset.UTC)
                                        logger.info {
                                            "[$symbol] " +
                                                    "KLINE TS $date " +
                                                    "OHLC(${kLine.open.roundToInt()} ${kLine.high.roundToInt()} " +
                                                    "${kLine.low.roundToInt()} ${kLine.close.roundToInt()}) " +
                                                    "vol ${kLine.volume}"
                                        }
                                    }
                                }
                                else -> logger.info { "Could not decode $it" }
                            }

                        }

                    }
                } catch (e: ClosedReceiveChannelException) {
                    logger.warn(e) { "Error received" }
                    logger.warn("onClose ${closeReason.await()}")
                } catch (e: Throwable) {
                    logger.warn(e) { "Error received" }
                    logger.warn("onError ${closeReason.await()}")
                }

            }

        }
    }

}


