/*
 * Copyright by the original author or authors.
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

import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bitcoinj.wallet.listeners.ScriptsChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;

import java.io.File;
import java.util.List;

import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;

/**
 * The following example shows how to use the by bitcoinj provided WalletAppKit.
 * The WalletAppKit class wraps the boilerplate (Peers, BlockChain, BlockStorage, Wallet) needed to set up a new SPV bitcoinj app.
 * 
 * In this example we also define a WalletEventListener class with implementors that are called when the wallet changes (for example sending/receiving money)
 */

/**
 * 下面的例子展示了如何使用由bitcoinj提供的WalletAppKit。
 * WalletAppKit类封装了建立一个新的SPV bitcoinj应用程序所需的样板文件(对等点、区块链、BlockStorage、Wallet)。
 * 在本例中，我们还定义了一个WalletEventListener类，该类具有在钱包更改时调用的实现者(例如发送/接收货币)
 *
 */
public class Kit {

    public static void main(String[] args) {

        // First we configure the network we want to use.首先我们配置我们想要使用的网络。
        // The available options are:可用的选项是:
        // - MainNetParams 公共测试网络
        // - TestNet3Params 生产网络
        // - RegTestParams 私有测试网络
        // While developing your application you probably want to use the Regtest mode and run your local bitcoin network. Run bitcoind with the -regtest flag
        //在开发应用程序时，您可能希望使用Regtest模式并运行本地比特币网络。使用- regtest标记运行bitcoind
        // To test you app with a real network you can use the testnet. The testnet is an alternative bitcoin network that follows the same rules as main network. Coins are worth nothing and you can get coins for example from http://faucet.xeno-genesis.com/
        // 用一个真实的网络测试你的应用程序，你可以使用testnet。testnet是一个替代的比特币网络，它遵循与主网络相同的规则。硬币一文不值，你可以从http://faucet.xeno - genesis.com/获得硬币
        // For more information have a look at: https://bitcoinj.github.io/testing and https://bitcoin.org/en/developer-examples#testing-applications
        //更多信息请看:https://bitcoinj.github。io /测试和https://bitcoin.org/en/developer-examples应用程序测试
        NetworkParameters params = TestNet3Params.get();

        // Now we initialize a new WalletAppKit. The kit handles all the boilerplate for us and is the easiest way to get everything up and running.
        //现在我们初始化一个新的WalletAppKit。这个工具箱为我们处理所有的样板，是把所有的东西都准备好并运行的最简单的方法。
        // Have a look at the WalletAppKit documentation and its source to understand what's happening behind the scenes: https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/kits/WalletAppKit.java
        //看看WalletAppKit文档及其来源理解幕后发生了什么:https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/kits/WalletAppKit.java
        WalletAppKit kit = new WalletAppKit(params, new File("."), "walletappkit-example");

        // In case you want to connect with your local bitcoind tell the kit to connect to localhost.
        //如果您想要连接本地的bitcoind，请告诉该工具包连接到本地主机。
        // You must do that in reg test mode.
        //必须在reg测试模式下做到这一点。

        //kit.connectToLocalHost();

        // Now we start the kit and sync the blockchain.
        //现在我们启动工具包并同步区块链。
        // bitcoinj is working a lot with the Google Guava libraries. The WalletAppKit extends the AbstractIdleService. Have a look at the introduction to Guava services: https://github.com/google/guava/wiki/ServiceExplained
        kit.startAsync();
        kit.awaitRunning();

        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                System.out.println("-----> coins resceived: " + tx.getHashAsString());
                System.out.println("received: " + tx.getValue(wallet));
            }
        });

        kit.wallet().addCoinsSentEventListener(new WalletCoinsSentEventListener() {
            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                System.out.println("coins sent");
            }
        });

        kit.wallet().addKeyChainEventListener(new KeyChainEventListener() {
            @Override
            public void onKeysAdded(List<ECKey> keys) {
                System.out.println("new key added");
            }
        });

        kit.wallet().addScriptsChangeEventListener(new ScriptsChangeEventListener() {
            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                System.out.println("new script added");
            }
        });

        kit.wallet().addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                System.out.println("-----> confidence changed: " + tx.getHashAsString());
                TransactionConfidence confidence = tx.getConfidence();
                System.out.println("new block depth: " + confidence.getDepthInBlocks());
            }
        });

        // Ready to run. The kit syncs the blockchain and our wallet event listener gets notified when something happens.
        //准备好运行。该组件同步区块链和我们的钱包事件监听器在发生什么事情时得到通知。
        // To test everything we create and print a fresh receiving address. Send some coins to that address and see if everything works.
        //测试我们所创建的所有东西并打印一个新的接收地址。把一些硬币寄到那个地址，看看是否一切正常。
        System.out.println("send money to: " + kit.wallet().freshReceiveAddress().toString());

        // Make sure to properly shut down all the running services when you manually want to stop the kit. The WalletAppKit registers a runtime ShutdownHook so we actually do not need to worry about that when our application is stopping.
        //当您手动停止该工具包时，确保正确地关闭所有正在运行的服务。WalletAppKit注册了一个运行时ShutdownHook，因此当我们的应用程序停止时，我们实际上不需要担心它。
        //System.out.println("shutting down again");
        //kit.stopAsync();
        //kit.awaitTerminated();
    }

}
