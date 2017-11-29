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

import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.File;

/**
 * The following example shows you how to restore a HD wallet from a previously generated deterministic seed.
 * In this example we manually setup the blockchain, peer group, etc. You can also use the WalletAppKit which provides a restoreWalletFromSeed function to load a wallet from a deterministic seed.
 */

/**
 * 下面的例子向您展示了如何从以前生成的确定性种子中恢复一个HD钱包。
 * 在本例中，我们手动设置区块链、对等组等。您还可以使用WalletAppKit，它提供了一个restoreWalletFromSeed函数，以从一个确定的种子中加载一个钱包。
 */
public class RestoreFromSeed {

    public static void main(String[] args) throws Exception {
        NetworkParameters params = TestNet3Params.get();

        // Bitcoinj supports hierarchical deterministic wallets (or "HD Wallets"): https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki
        //Bitcoinj支持等级确定的钱包(或“高清钱包”):https://github.com/bitcoin/bips/blob/master/bip - 0032. mediawiki
        // HD wallets allow you to restore your wallet simply from a root seed. This seed can be represented using a short mnemonic sentence as described in BIP 39: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
        //高清钱包让你可以简单地从根种子中恢复钱包。这种子可以使用短记忆句子中描述毕普39:https://github.com/bitcoin/bips/blob/master/bip - 0039.mediawiki

        // Here we restore our wallet from a seed with no passphrase. Also have a look at the BackupToMnemonicSeed.java example that shows how to backup a wallet by creating a mnemonic sentence.
        //在这里，我们从没有密码的种子中恢复我们的钱包。也看一下BackupToMnemonicSeed。java示例演示如何通过创建助记句来备份钱包。
        String seedCode = "yard impulse luxury drive today throw farm pepper survey wreck glass federal";
        String passphrase = "";
        Long creationtime = 1409478661L;

        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);

        // The wallet class provides a easy fromSeed() function that loads a new wallet from a given seed.
        //钱包类提供了一个简单的fromSeed()函数，该函数从一个给定的种子加载一个新的钱包。
        Wallet wallet = Wallet.fromSeed(params, seed);

        // Because we are importing an existing wallet which might already have transactions we must re-download the blockchain to make the wallet picks up these transactions
        //因为我们正在导入可能已经有交易的现有钱包，我们必须重新下载区块链以使钱包获得这些交易
        // You can find some information about this in the guides: https://bitcoinj.github.io/working-with-the-wallet#setup
        //你可以找到一些信息关于这个指南:https://bitcoinj.github.io/working-with-the-wallet设置
        // To do this we clear the transactions of the wallet and delete a possible existing blockchain file before we download the blockchain again further down.
        //为了完成这个任务，我们清除了钱包的交易，删除了一个可能存在的区块链文件，然后再进一步下载区块链。
        System.out.println(wallet.toString());
        wallet.clearTransactions(0);
        File chainFile = new File("restore-from-seed.spvchain");
        if (chainFile.exists()) {
            chainFile.delete();
        }

        // Setting up the BlochChain, the BlocksStore and connecting to the network.
        //设置BlochChain、BlocksStore并连接到网络。
        SPVBlockStore chainStore = new SPVBlockStore(params, chainFile);
        BlockChain chain = new BlockChain(params, chainStore);
        PeerGroup peers = new PeerGroup(params, chain);
        peers.addPeerDiscovery(new DnsDiscovery(params));

        // Now we need to hook the wallet up to the blockchain and the peers. This registers event listeners that notify our wallet about new transactions.
        //现在我们需要把钱包与区块链和其他同伴联系起来。这个寄存器事件监听器将新事务通知我们的钱包
        chain.addWallet(wallet);
        peers.addWallet(wallet);

        DownloadProgressTracker bListener = new DownloadProgressTracker() {
            @Override
            public void doneDownload() {
                System.out.println("blockchain downloaded");
            }
        };

        // Now we re-download the blockchain. This replays the chain into the wallet. Once this is completed our wallet should know of all its transactions and print the correct balance.
        //现在我们重新下载区块链。这把链子拉进了钱包。一旦完成，我们的钱包应该知道所有的交易并打印正确的余额。
        peers.start();
        peers.startBlockChainDownload(bListener);

        bListener.await();

        // Print a debug message with the details about the wallet. The correct balance should now be displayed.
        //用钱包的细节打印一条调试信息。现在应该显示正确的余额。
        System.out.println(wallet.toString());

        // shutting down again
        //再次关闭
        peers.stop();
    }
}
