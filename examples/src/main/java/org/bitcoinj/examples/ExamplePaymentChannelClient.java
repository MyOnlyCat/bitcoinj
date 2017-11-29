/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.examples;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.protocols.channels.*;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static org.bitcoinj.core.Coin.CENT;

/**
 * Simple client that connects to the given host, opens a channel, and pays one cent.
 */

/**
 * 连接到给定主机的简单客户机，打开一个通道，并支付1美分。
 */
public class ExamplePaymentChannelClient {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ExamplePaymentChannelClient.class);
    private WalletAppKit appKit;
    private final Coin channelSize;
    private final ECKey myKey;
    private final NetworkParameters params;

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();
        OptionParser parser = new OptionParser();
        OptionSpec<NetworkEnum> net = parser.accepts("net", "The network to run the examples on").withRequiredArg().ofType(NetworkEnum.class).defaultsTo(NetworkEnum.TEST);
        OptionSpec<Integer> version = parser.accepts("version", "The payment channel protocol to use").withRequiredArg().ofType(Integer.class);
        parser.accepts("help", "Displays program options");
        OptionSet opts = parser.parse(args);
        if (opts.has("help") || !opts.has(net) || opts.nonOptionArguments().size() != 1) {
            System.err.println("usage: ExamplePaymentChannelClient --net=MAIN/TEST/REGTEST --version=1/2 host");
            parser.printHelpOn(System.err);
            return;
        }
        IPaymentChannelClient.ClientChannelProperties clientChannelProperties = new PaymentChannelClient.DefaultClientChannelProperties(){
            @Override
            public PaymentChannelClient.VersionSelector versionSelector() { return PaymentChannelClient.VersionSelector.VERSION_1; }
        };

        if (opts.has("version")) {
            switch (version.value(opts)) {
                case 1:
                    // Keep the default
                    break;
                case 2:
                    clientChannelProperties = new PaymentChannelClient.DefaultClientChannelProperties(){
                        @Override
                        public PaymentChannelClient.VersionSelector versionSelector() { return PaymentChannelClient.VersionSelector.VERSION_2; }
                    };
                    break;
                default:
                    System.err.println("Invalid version - valid versions are 1, 2");
                    return;
            }
        }
        NetworkParameters params = net.value(opts).get();
        new ExamplePaymentChannelClient().run(opts.nonOptionArguments().get(0), clientChannelProperties, params);
    }

    public ExamplePaymentChannelClient() {
        channelSize = CENT;
        myKey = new ECKey();
        params = RegTestParams.get();
    }

    public void run(final String host, IPaymentChannelClient.ClientChannelProperties clientChannelProperties, final NetworkParameters params) throws Exception {
        // Bring up all the objects we need, create/load a wallet, sync the chain, etc. We override WalletAppKit so we
        // can customize it by adding the extension objects - we have to do this before the wallet file is loaded so
        // the plugin that knows how to parse all the additional data is present during the load.

        //打开我们需要的所有对象，创建/加载一个钱包，同步链条等等，我们就可以超越WalletAppKit
        //可以通过添加扩展对象来定制它——我们必须在加载钱包文件之前做到这一点
        //在加载过程中，知道如何解析所有额外数据的插件。
        appKit = new WalletAppKit(params, new File("."), "payment_channel_example_client") {
            @Override
            protected List<WalletExtension> provideWalletExtensions() {
                // The StoredPaymentChannelClientStates object is responsible for, amongst other things, broadcasting
                // the refund transaction if its lock time has expired. It also persists channels so we can resume them
                // after a restart.
                // We should not send a PeerGroup in the StoredPaymentChannelClientStates constructor
                // since WalletAppKit will find it for us.

                //StoredPaymentChannelClientStates对象负责,在其他事情上,广播
                //如果它的锁时间过期了，退款事务。它还能保持频道，这样我们就可以重新开始
                //重新启动后。
                //我们不应该发送一个PeerGroup StoredPaymentChannelClientStates构造函数
                //因为WalletAppKit会为我们找到它。
                return ImmutableList.<WalletExtension>of(new StoredPaymentChannelClientStates(null));
            }
        };
        // Broadcasting can take a bit of time so we up the timeout for "real" networks
        //广播可能需要一点时间，所以我们暂停了“真实”网络
        final int timeoutSeconds = params.getId().equals(NetworkParameters.ID_REGTEST) ? 15 : 150;
        if (params == RegTestParams.get()) {
            appKit.connectToLocalHost();
        }
        appKit.startAsync();
        appKit.awaitRunning();
        // We now have active network connections and a fully synced wallet.
        // Add a new key which will be used for the multisig contract.

        //我们现在有了活跃的网络连接和一个完全同步的钱包。
        //添加一个新的密钥，该密钥将用于多用户合同。
        appKit.wallet().importKey(myKey);
        appKit.wallet().allowSpendingUnconfirmedTransactions();

        System.out.println(appKit.wallet());

        // Create the object which manages the payment channels protocol, client side. Tell it where the server to
        // connect to is, along with some reasonable network timeouts, the wallet and our temporary key. We also have
        // to pick an amount of value to lock up for the duration of the channel.
        //
        // Note that this may or may not actually construct a new channel. If an existing unclosed channel is found in
        // the wallet, then it'll re-use that one instead.

        //创建管理支付通道协议的对象，客户端。告诉它服务器在哪里
        //连接到is，连同一些合理的网络超时，钱包和我们的临时钥匙。我们也有
        //在通道的持续时间内选择一定数量的锁。
        //注意，这可能实际上并不构成一个新频道。如果存在一个现有的未闭合通道
        //钱包，然后它会重新使用那个钱包。
        final InetSocketAddress server = new InetSocketAddress(host, 4242);

        waitForSufficientBalance(channelSize);
        final String channelID = host;
        // Do this twice as each one sends 1/10th of a bitcent 5 times, so to send a bitcent, we do it twice. This
        // demonstrates resuming a channel that wasn't closed yet. It should close automatically once we run out
        // of money on the channel.

        //这样做两次，因为每个都要发送1 / 10个比特的5倍，所以发送一个比特，我们做两次。这个
        //演示了恢复尚未关闭的通道。一旦我们跑完，它应该自动关闭
        //在通道上的钱。
        log.info("Round one ...");
        openAndSend(timeoutSeconds, server, channelID, 5, clientChannelProperties);
        log.info("Round two ...");
        log.info(appKit.wallet().toString());
        openAndSend(timeoutSeconds, server, channelID, 4, clientChannelProperties);   // 4 times because the opening of the channel made a payment.
        log.info("Stopping ...");
        appKit.stopAsync();
        appKit.awaitTerminated();
    }

    private void openAndSend(int timeoutSecs, InetSocketAddress server, String channelID, final int times, IPaymentChannelClient.ClientChannelProperties clientChannelProperties) throws IOException, ValueOutOfRangeException, InterruptedException {
        // Use protocol version 1 for simplicity  为了简单起见，使用协议版本1
        PaymentChannelClientConnection client = new PaymentChannelClientConnection(
                server, timeoutSecs, appKit.wallet(), myKey, channelSize, channelID, null, clientChannelProperties);
        // Opening the channel requires talking to the server, so it's asynchronous. 打开通道需要与服务器对话，所以它是异步的。
        final CountDownLatch latch = new CountDownLatch(1);
        Futures.addCallback(client.getChannelOpenFuture(), new FutureCallback<PaymentChannelClientConnection>() {
            @Override
            public void onSuccess(PaymentChannelClientConnection client) {
                // By the time we get here, if the channel is new then we already made a micropayment! The reason is,
                // we are not allowed to have payment channels that pay nothing at all.

                //当我们到达这里的时候，如果频道是新的，那么我们已经做了微支付!原因是,
                //我们不允许有支付任何费用的支付渠道。
                log.info("Success! Trying to make {} micropayments. Already paid {} satoshis on this channel",
                        times, client.state().getValueSpent());
                final Coin MICROPAYMENT_SIZE = CENT.divide(10);
                for (int i = 0; i < times; i++) {
                    try {
                        // Wait because the act of making a micropayment is async, and we're not allowed to overlap.
                        // This callback is running on the user thread (see the last lines in openAndSend) so it's safe
                        // for us to block here: if we didn't select the right thread, we'd end up blocking the payment
                        // channels thread and would deadlock.

                        //等等，因为微支付的行为是异步的，我们不允许重叠。
                        //这个回调正在用户线程上运行(请参阅openAndSend的最后一行)，因此它是安全的
                        //我们在此阻止:如果我们没有选择正确的线程，我们最终会阻止支付
                        //通道线程和将死锁。
                        Uninterruptibles.getUninterruptibly(client.incrementPayment(MICROPAYMENT_SIZE));
                    } catch (ValueOutOfRangeException e) {
                        log.error("Failed to increment payment by a CENT, remaining value is {}", client.state().getValueRefunded());
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        log.error("Failed to increment payment", e);
                        throw new RuntimeException(e);
                    }
                    log.info("Successfully sent payment of one CENT, total remaining on channel is now {}", client.state().getValueRefunded());
                }
                if (client.state().getValueRefunded().compareTo(MICROPAYMENT_SIZE) < 0) {
                    // Now tell the server we're done so they should broadcast the final transaction and refund us what's
                    // left. If we never do this then eventually the server will time out and do it anyway and if the
                    // server goes away for longer, then eventually WE will time out and the refund tx will get broadcast
                    // by ourselves.

                    //现在告诉服务器我们已经完成了，他们应该广播最终的交易，并给我们退款
                    //左边。如果我们不这样做，服务器最终会超时，如果
                    //服务器离开时间较长，最终我们将超时，退款tx将会播出
                    //我们自己。
                    log.info("Settling channel for good");
                    client.settle();
                } else {
                    // Just unplug from the server but leave the channel open so it can resume later.
                    //从服务器上拔下插头，然后打开通道，这样它就可以在以后恢复。
                    client.disconnectWithoutSettlement();
                }
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("Failed to open connection", throwable);
                latch.countDown();
            }
        }, Threading.USER_THREAD);
        latch.await();
    }

    private void waitForSufficientBalance(Coin amount) {
        // Not enough money in the wallet. 钱包里没有足够的钱
        Coin amountPlusFee = amount.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        // ESTIMATED because we don't really need to wait for confirmation. 估计是因为我们不需要等待确认。
        ListenableFuture<Coin> balanceFuture = appKit.wallet().getBalanceFuture(amountPlusFee, Wallet.BalanceType.ESTIMATED);
        if (!balanceFuture.isDone()) {
            System.out.println("Please send " + amountPlusFee.toFriendlyString() +
                    " to " + myKey.toAddress(params));
            Futures.getUnchecked(balanceFuture);
        }
    }
}
