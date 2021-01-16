import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime


/**
 *
 * Created on 1/3/21.
 * @author Stephane Leclercq
 */
fun Double.round(decimals: Int = 2): Double = "%.${decimals}f".format(this).toDouble()
fun Double.roundForOrder(decimals: Int = 2): String = this.round(decimals).toBigDecimal().toPlainString()

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
                call.respondText("omghi")
            }
            get ("/order") {
                val orderRequest = OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT,
                    "0.001", "34000.0")
                // TODO create a generic function
                for (i in 1..5) {
                    val response = createOrder(orderRequest)
                    if (response.status.isSuccess()) {
                        call.respondText(response.readText())
                        return@get
                    }
                    log.info("Call error : $response")
                    delay(2000)
                }
            }
            // test : http://localhost:
            get ("/batchOrders") {
                val queryParameters: Parameters = call.parameters
                val spreadLow: Double = call.parameters["spreadLow"]?.toDouble() ?: 0.0
                val spreadHigh: Double = call.parameters["spreadHigh"]?.toDouble() ?: 0.0
                val totalQuantity = call.parameters["totalQuantity"]?.toDouble() ?: 0.0
                val increment = (spreadHigh - spreadLow) / (MAX_ORDERS_PER_BATCH - 1)
                var orderList = listOf<OrderRequest>()
                var price = spreadLow
                while (price <= spreadHigh) {
                    val quantity = (totalQuantity / MAX_ORDERS_PER_BATCH).roundForOrder(3)
                    orderList = orderList.plus(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, quantity, price.toString()))
                    price += increment
                }
                // TODO create a generic function
                for (i in 1..5) {
                    val response = batchOrders(orderList)
                    if (response.status.isSuccess()) {
                        call.respondText(response.readText())
                        return@get
                    }
                    log.info("Call error : $response")
                    delay(2000)
                }
            }
        }
    }.start(wait = true)


}

/*
suspend fun <T> performAction(param: T, action: (T) -> HttpResponse): Unit {
    val test: String? = "test"
    println(test?.length ?: "empty")
    action(param)
    test.let {  }


}


*/
