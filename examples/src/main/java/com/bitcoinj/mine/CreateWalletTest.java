package com.bitcoinj.mine;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;

public class CreateWalletTest {
    public static void main(String[] args) {
        final NetworkParameters netParams = NetworkParameters.testNet();
        //试着从存储中读取钱包，如果不可能，创建一个新的
        Wallet wallet = null;
        final File walletFile = new File("test.wallet");
        try {
         wallet = new Wallet(netParams);
         for (int i = 0; i < 5; i++){

             //创建一个密钥并将其添加到钱包中
             wallet.addKey(new ECKey());

             //将钱包内容保存到磁盘
             wallet.saveToFile(walletFile);
         }
        }catch (IOException e){
            System.out.println("无法创建钱包文件");
        }

        //直接从keychain ArrayList获取钱包的第一个密钥
        ECKey firstKey = wallet.getImportedKeys().get(0);

        //输出key
        System.out.println("First key\n" + firstKey);

        //这是整个钱包
        System.out.println("完整的钱包内容/n" + wallet);

        //我们可以使用公钥的散列
        //检查钥匙对是否在这个钱夹里
        if (wallet.isPubKeyHashMine(firstKey.getPubKeyHash())){
            System.out.println("是我的key");
        }else {
            System.out.println("钥匙不是这个钱包里的");
        }
    }
}
