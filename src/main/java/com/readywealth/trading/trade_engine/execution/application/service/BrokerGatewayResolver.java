package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.BrokerGateway;
import com.readywealth.trading.trade_engine.execution.domain.BrokerType;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class BrokerGatewayResolver {

    private final BrokerGateway paperBrokerGateway;
    private final BrokerGateway kiteBrokerGateway;

    public BrokerGatewayResolver(
            @Qualifier("paperBrokerGateway") BrokerGateway paperBrokerGateway,
            @Qualifier("kiteBrokerGateway") BrokerGateway kiteBrokerGateway) {
        this.paperBrokerGateway = paperBrokerGateway;
        this.kiteBrokerGateway = kiteBrokerGateway;
    }

    public BrokerGateway resolve(TradingAccount account) {
        if (account.mode() == TradingMode.LIVE || account.brokerType() == BrokerType.KITE) {
            return kiteBrokerGateway;
        }
        return paperBrokerGateway;
    }
}
