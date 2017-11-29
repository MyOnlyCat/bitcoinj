package com.bitcoinj.mine;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

public class TestKey {
    public static void main(String[] args) {
        ECKey ceKey = new ECKey();
        NetworkParameters params = TestNet3Params.get();
        String pKey = ceKey.toAddress(params).toBase58(); // base58编码后的地址

        System.out.println("pKey = " + pKey);
    }

    /**
     * 生成私钥字节数组
     * @return 私钥字节数组
     */
    public static byte[] createPrivateKeyForByteArray() {
        ECKey ceKey = new ECKey();
        return ceKey.getPrivKeyBytes();
    }

    /**
     * 生成字符串形式私钥
     * @return 私钥字符串
     */
    public static String createPrivateKeyForString() {
        return new String(createPrivateKeyForByteArray());
    }

    /**
     * 生成公钥字节数组
     * @return 公钥字节数组
     */
    public static byte[] createPublicKeyForByteArray() {
        ECKey ceKey = new ECKey();
        return ceKey.getPubKey();
    }

    /**
     * 生成字符串形式公钥
     * @return 公钥字符串
     */
    public static String createPublicKeyForString() {
        return new String(createPublicKeyForByteArray());
    }
}
