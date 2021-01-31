/**
 *
 * Created on 1/23/21.
 * @author Stephane Leclercq
 */

data class Position(val currentQty: String)
data class Instrument(val midPrice: String?)
data class TickerData(val todo: String)
data class ExchangeOrder(val id: Long, val side: Side,
                         val price: String, val orderQty: String)
enum class Side { BUY, SELL }

interface ExchangeConnector {

    fun cancel(vararg orderIds: Long): List<ExchangeOrder>
    fun openOrders(): List<ExchangeOrder>
    fun httpOpenOrders(): List<ExchangeOrder>
    fun position(s: String): Position
    fun isOpen(): Boolean
    fun instrument(s: String): Instrument
    fun tickerData(s: String): TickerData
    fun amendBulkOrders(orders: List<ExchangeOrder>): List<ExchangeOrder>
    fun createBulkOrders(orders: List<ExchangeOrder>): List<ExchangeOrder>

}
