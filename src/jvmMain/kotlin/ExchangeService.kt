import kotlinx.coroutines.delay
import mu.KotlinLogging

/**
 *
 * Created on 1/23/21.
 * @author Stephane Leclercq
 */
const val API_REST_INTERVAL = 2000L

private val logger = KotlinLogging.logger {}

class ExchangeService(val dryRun: Boolean = true,
                      val symbol: String,
                      val exchangeConnector: ExchangeConnector) {

    suspend fun cancelOrder(order: ExchangeOrder) {
        logger.info { "Canceling: ${order.side} ${order.orderQty} ${order.price}" }
        if (dryRun) {
            return
        }
        exchangeConnector.cancel(order.id)
        delay(API_REST_INTERVAL)
    }

    suspend fun cancelAllOrders() {
        logger.info { "Resetting current position. Canceling all existing orders." }
        if (dryRun) {
            return
        }
        val orders = exchangeConnector.httpOpenOrders()
        orders.forEach { logger.info { "Canceling: ${it.side} ${it.orderQty} ${it.price}" } }
        exchangeConnector.cancel(*orders.map { it.id }.toLongArray())
        delay(API_REST_INTERVAL)
    }

    suspend fun amendBulkOrders(orders: List<ExchangeOrder>) =
        if (dryRun) orders else exchangeConnector.amendBulkOrders(orders)

    suspend fun createBulkOrders(orders: List<ExchangeOrder>) =
        if (dryRun) orders else exchangeConnector.createBulkOrders(orders)

    suspend fun cancelBulkOrders(orders: List<ExchangeOrder>) =
        if (dryRun) orders else exchangeConnector.cancel(*orders.map { it.id }.toLongArray())


    suspend fun getDelta(symbol: String = this.symbol) = getPosition(symbol).currentQty

    suspend fun getInstrument(symbol: String = this.symbol) = exchangeConnector.instrument(symbol)

    suspend fun getOrders(): List<ExchangeOrder> = if (dryRun) listOf() else exchangeConnector.openOrders()

    suspend fun getHighestBuy() =
        getOrders().filter { it.side == Side.BUY }
            .maxOfOrNull { it.price.toDouble() } ?: Double.MIN_VALUE

    suspend fun getLowestSell() =
        getOrders().filter { it.side == Side.SELL }
            .minOfOrNull { it.price.toDouble() } ?: Double.MAX_VALUE

    suspend fun getPosition(symbol: String = this.symbol) = exchangeConnector.position(symbol)

    suspend fun getTicker(symbol: String = this.symbol) = exchangeConnector.tickerData(symbol)

    suspend fun isOpen() = exchangeConnector.isOpen()

    suspend fun checkIfOrderbookEmpty() {
        val instrument = getInstrument()
        if (instrument.midPrice == null) {
            throw Exception("Orderbook is empty, cannot quote")
        }
    }




}
