package model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Captures the key elements of a stock trade, such as the ticker symbol, price,
 * number of shares, the type of the trade (buy or sell), and an id uniquely identifying
 * the trade.
 */
public class StockTrade {
    private final static ObjectMapper JSON = new ObjectMapper();

    static {
        JSON.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Represents the type of the stock trade eg buy or sell.
     */
    public enum TradeType {
        BUY,
        SELL
    }

    private String tickerSymbol;
    private TradeType tradeType;
    private double price;
    private long quantity;
    private long id;

    public StockTrade() {
    }

    public StockTrade(String tickerSymbol, TradeType tradeType, double price, long quantity, long id) {
        this.tickerSymbol = tickerSymbol;
        this.tradeType = tradeType;
        this.price = price;
        this.quantity = quantity;
        this.id = id;
    }

    public String getTickerSymbol() {
        return tickerSymbol;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public double getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getId() {
        return id;
    }

    public byte[] toJsonAsBytes() {
        try {
            return JSON.writeValueAsBytes(this);
        } catch (IOException e) {
            return null;
        }
    }

    public static StockTrade fromJsonAsBytes(byte[] bytes) {
        try {
            return JSON.readValue(bytes, StockTrade.class);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("ID %d: %s %d shares of %s for $%.02f",
                id, tradeType, quantity, tickerSymbol, price);
    }
}
