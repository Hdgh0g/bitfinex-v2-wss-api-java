package com.github.jnidzwetzki.bitfinex.v2.test.integration;

import java.util.concurrent.CountDownLatch;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.PooledBitfinexApiBroker;
import com.github.jnidzwetzki.bitfinex.v2.command.SubscribeCandlesCommand;
import com.github.jnidzwetzki.bitfinex.v2.command.SubscribeTickerCommand;
import com.github.jnidzwetzki.bitfinex.v2.command.SubscribeTradesCommand;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexCandleTimeFrame;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;

public class PooledBitfinexApiBrokerTest {

    @BeforeClass
    public static void registerDefaultCurrencyPairs() {
        BitfinexCurrencyPair.registerDefaults();
    }

    @AfterClass
    public static void unregisterDefaultCurrencyPairs() {
        BitfinexCurrencyPair.unregisterAll();
    }

    @Test(timeout = 120_000)
    public void testSubscriptions() throws InterruptedException {
        // given
        final int channelLimit = 20;
        final int channelsPerConnection = 10;
    	
        final BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        final PooledBitfinexApiBroker client = 
        		(PooledBitfinexApiBroker) BitfinexClientFactory.newPooledClient(config, channelsPerConnection);

        // when
        final CountDownLatch subsLatch = new CountDownLatch(channelLimit * 3);
        client.getCallbacks().onSubscribeChannelEvent(chan -> {
        		subsLatch.countDown();
        		System.out.println("Got subscribed event: " + chan + " " + subsLatch.getCount());
        });

        client.connect();
        
        BitfinexCurrencyPair.values().stream()
                .limit(channelLimit)
                .forEach(bfxPair -> {
                    client.sendCommand(new SubscribeCandlesCommand(BitfinexSymbols.candlesticks(bfxPair, BitfinexCandleTimeFrame.MINUTES_1)));
                    // Not all currency's have a orderbook (e.g., CFI:USD)
                    // client.sendCommand(new SubscribeOrderbookCommand(BitfinexSymbols.orderBook(bfxPair, BitfinexOrderBookSymbol.Precision.P0, BitfinexOrderBookSymbol.Frequency.F0, 100)));
                    client.sendCommand(new SubscribeTickerCommand(BitfinexSymbols.ticker(bfxPair)));
                    client.sendCommand(new SubscribeTradesCommand(BitfinexSymbols.executedTrades(bfxPair)));
                });
        
       
        // then
        subsLatch.await();
        Assert.assertEquals(channelLimit * 3, client.getSubscribedChannels().size());
        Assert.assertEquals(channelLimit * 3 / channelsPerConnection, client.websocketConnCount());
    }

}