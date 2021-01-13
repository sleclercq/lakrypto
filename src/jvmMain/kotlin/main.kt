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
                call.respondText(createOrder(orderRequest))
            }
            get ("/batchOrders") {
                val spreadLow = 34000
                val spreadHigh = 34100
                val increment = (spreadHigh - spreadLow) / (MAX_ORDERS_PER_BATCH - 1)
                val totalQuantity = 0.01
                var orderList = listOf<OrderRequest>()
                for (price in spreadLow..spreadHigh step increment) {
                    val quantity = (totalQuantity / MAX_ORDERS_PER_BATCH).roundForOrder(3)
                    orderList = orderList.plus(OrderRequest("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, quantity, price.toString()))
                }
                call.respondText(batchOrders(orderList))
            }
        }
    }.start(wait = true)


}

