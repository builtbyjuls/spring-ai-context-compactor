package com.builtbyjuls.clearing.messaging;

import com.builtbyjuls.clearing.trade.TradeClearingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TradeExecutionRabbitListener {

    private final TradeClearingService tradeClearingService;

    public TradeExecutionRabbitListener(TradeClearingService tradeClearingService) {
        this.tradeClearingService = tradeClearingService;
    }

    @RabbitListener(id = "tradeInboundListener", queues = ClearingAmqpConfig.TRADE_INBOUND_QUEUE, concurrency = "1-2")
    public void onTradeSubmitted(TradeSubmissionMessage message) {
        tradeClearingService.novateAndClearTrade(message);
    }
}
