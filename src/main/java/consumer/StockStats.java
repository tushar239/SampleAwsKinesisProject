package consumer;

import model.StockTrade;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static model.StockTrade.TradeType;

/**
 * Maintains running statistics of stock trades passed to it.
 */
public class StockStats {
    // Keeps count of trades for each ticker symbol for each trade type
    private EnumMap<TradeType, Map<String, Long>> countsByTradeType;

    // Keeps the ticker symbol for the most popular stock for each trade type
    private EnumMap<TradeType, String> mostPopularByTradeType;

    /**
     * Constructor.
     */
    public StockStats() {
        countsByTradeType = new EnumMap<TradeType, Map<String, Long>>(TradeType.class);
        for (TradeType tradeType : TradeType.values()) {
            countsByTradeType.put(tradeType, new HashMap<String, Long>());
        }

        mostPopularByTradeType = new EnumMap<TradeType, String>(TradeType.class);
    }

    /**
     * Updates the statistics taking into account the new stock trade received.
     *
     * @param trade Stock trade instance
     */
    public void addStockTrade(StockTrade trade) {
        // update buy/sell count
        TradeType type = trade.getTradeType();
        Map<String, Long> counts = countsByTradeType.get(type);
        Long count = counts.get(trade.getTickerSymbol());
        if (count == null) {
            count = 0L;
        }
        counts.put(trade.getTickerSymbol(), ++count);

        // update most popular stock
        String mostPopular = mostPopularByTradeType.get(type);
        if (mostPopular == null ||
                countsByTradeType.get(type).get(mostPopular) < count) {
            mostPopularByTradeType.put(type, trade.getTickerSymbol());
        }
    }

    public String toString() {
        return String.format(
                "Most popular stock being bought: %s, %d buys.%n" +
                        "Most popular stock being sold: %s, %d sells.",
                getMostPopularStock(TradeType.BUY), getMostPopularStockCount(TradeType.BUY),
                getMostPopularStock(TradeType.SELL), getMostPopularStockCount(TradeType.SELL));
    }

    private String getMostPopularStock(TradeType tradeType) {
        return mostPopularByTradeType.get(tradeType);
    }

    private Long getMostPopularStockCount(TradeType tradeType) {
        String mostPopular = getMostPopularStock(tradeType);
        return countsByTradeType.get(tradeType).get(mostPopular);
    }
}
