package consumer;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;

/**
 * Used to create new stock trade record processors.
 */
public class StockTradeRecordProcessorFactory implements IRecordProcessorFactory {
    /**
     * Constructor.
     */
    public StockTradeRecordProcessorFactory() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRecordProcessor createProcessor() {
        StockTradeRecordProcessor stockTradeRecordProcessor = new StockTradeRecordProcessor();
        return stockTradeRecordProcessor;
    }
}
