import BinanceService.Companion.MAX_ORDERS_PER_BATCH
import BinanceService.Companion.batchOrders
import BinanceService.Companion.createOrder
import BinanceService.Companion.miniTickers
import BinanceService.Companion.performWebSocket
import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime


/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */
fun Double.round(decimals: Int = 2): Double = "%.${decimals}f".format(this).toDouble()
fun Double.roundForOrder(decimals: Int = 2): String = this.round(decimals).toBigDecimal().toPlainString()

private val logger = KotlinLogging.logger {}

@ExperimentalTime
fun main() {
    logger.info { "Lakrypto starting..." }

    GlobalScope.launch {
        repeat(Int.MAX_VALUE) {
            logger.info { miniTickers.map {
                    entry -> "[${entry.key}] ${entry.value.value}"
            }.joinToString() }
            delay(10000L)
        }
    }

    GlobalScope.launch {
        //performWebSocket()
        BybitService.performWebSocket()
    }


    embeddedServer(Netty, port = 8000) {
        routing {
            get ("/") {
                call.respondText("omghi")
            }
            get ("/order") {
                val orderRequest = BinanceService.Companion.OrderRequest(
                    "BTCUSDT", BinanceService.Companion.OrderSide.BUY, BinanceService.Companion.OrderType.LIMIT,
                    "0.001", "34000.0"
                )
                callWithRetries { createOrder(orderRequest) }
            }
            // test : http://localhost:
            get ("/batchOrders") {
                val spreadLow: Double = call.parameters["spreadLow"]?.toDouble() ?: 0.0
                val spreadHigh: Double = call.parameters["spreadHigh"]?.toDouble() ?: 0.0
                val totalQuantity = call.parameters["totalQuantity"]?.toDouble() ?: 0.0
                val increment = (spreadHigh - spreadLow) / (MAX_ORDERS_PER_BATCH - 1)
                var orderList = listOf<BinanceService.Companion.OrderRequest>()
                var price = spreadLow
                while (price <= spreadHigh) {
                    val quantity = (totalQuantity / MAX_ORDERS_PER_BATCH).roundForOrder(3)
                    orderList = orderList.plus(
                        BinanceService.Companion.OrderRequest(
                            "BTCUSDT",
                            BinanceService.Companion.OrderSide.BUY,
                            BinanceService.Companion.OrderType.LIMIT,
                            quantity,
                            price.toString()
                        )
                    )
                    price += increment
                }
                callWithRetries { batchOrders(orderList) }
            }
        }
    }.start(wait = true)


}


suspend fun PipelineContext<Unit, ApplicationCall>.callWithRetries(action: suspend () -> HttpResponse) {
    for (i in 1..5) {
        val response = action()
        if (response.status.isSuccess()) {
            call.respondText(response.readText())
            return
        }
        call.application.environment.log.info("Call error : $response")
        delay(2000)
    }
}

