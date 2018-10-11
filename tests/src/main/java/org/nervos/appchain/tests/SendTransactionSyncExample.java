package org.nervos.appchain.tests;

import java.math.BigInteger;

import org.nervos.appchain.crypto.Credentials;
import org.nervos.appchain.protocol.Nervosj;
import org.nervos.appchain.protocol.core.DefaultBlockParameterName;
import org.nervos.appchain.protocol.core.methods.request.Transaction;
import org.nervos.appchain.protocol.core.methods.response.AppGetBalance;
import org.nervos.appchain.protocol.core.methods.response.AppSendTransaction;
import org.nervos.appchain.protocol.core.methods.response.TransactionReceipt;
import org.nervos.appchain.tx.response.PollingTransactionReceiptProcessor;
import org.nervos.appchain.utils.Convert;

public class SendTransactionSyncExample {

    static String payerKey;
    static String payeeKey;
    static String payeeAddr;
    static int chainId;
    static int version;
    static long quotaToTransfer;

    static Nervosj service;

    static {
        Config conf = new Config();
        conf.buildService(false);

        payerKey = conf.primaryPrivKey;
        payeeKey = conf.auxPrivKey1;
        payeeAddr = conf.auxAddr1;
        chainId = Integer.parseInt(conf.chainId);
        version = Integer.parseInt(conf.version);
        quotaToTransfer = Long.parseLong(conf.defaultQuotaDeployment);
        service = conf.service;
    }

    static BigInteger getBalance(String address) {
        BigInteger balance = null;
        try {
            AppGetBalance response = service
                    .appGetBalance(
                            address, DefaultBlockParameterName.LATEST).send();
            balance = response.getBalance();
        } catch (Exception e) {
            System.out.println("failed to get balance.");
            System.exit(1);
        }
        return balance;
    }

    //use PollingTransactionReceiptProcessor to wait for transaction receipt.
    static TransactionReceipt transferSync(
            String payerKey, String payeeAddr, String value)
            throws Exception {
        PollingTransactionReceiptProcessor txProcessor = new PollingTransactionReceiptProcessor(
                service,
                15 * 1000,
                40);

        Transaction tx = new Transaction(payeeAddr,
                TestUtil.getNonce(),
                quotaToTransfer,
                TestUtil.getValidUtilBlock(service).longValue(),
                version, chainId,
                value,
                "");

        String rawTx = tx.sign(payerKey, false, false);
        AppSendTransaction appSendTrasnction = service.appSendRawTransaction(rawTx).send();

        TransactionReceipt txReceipt = txProcessor
                .waitForTransactionReceipt(
                        appSendTrasnction.getSendTransactionResult().getHash());
        return txReceipt;
    }

    public static void main(String[] args) throws Exception {
        Credentials payerCredential = Credentials.create(payerKey);
        String payerAddr = payerCredential.getAddress();
        System.out.println(Convert.fromWei(getBalance(payerAddr).toString(), Convert.Unit.ETHER));
        System.out.println(Convert.fromWei(getBalance(payeeAddr).toString(), Convert.Unit.ETHER));

        String value = "1";
        String valueWei = Convert.toWei(value, Convert.Unit.ETHER).toString();

        TransactionReceipt txReceipt = transferSync(payerKey, payeeAddr, valueWei);

        if (txReceipt.getErrorMessage() == null) {
            System.out.println(
                    Convert.fromWei(getBalance(payerAddr).toString(), Convert.Unit.ETHER));
            System.out.println(
                    Convert.fromWei(getBalance(payeeAddr).toString(), Convert.Unit.ETHER));
        }
    }
}
