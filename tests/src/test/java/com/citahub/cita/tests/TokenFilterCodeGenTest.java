package com.citahub.cita.tests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;

import com.citahub.cita.crypto.Credentials;
import com.citahub.cita.crypto.sm2.SM2;
import com.citahub.cita.protocol.CITAj;
import com.citahub.cita.protocol.core.DefaultBlockParameter;
import com.citahub.cita.protocol.core.DefaultBlockParameterName;
import com.citahub.cita.protocol.core.methods.request.Transaction;
import com.citahub.cita.tx.RawTransactionManager;
import com.citahub.cita.tx.TransactionManager;
import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import java.math.BigInteger;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class TokenFilterCodeGenTest {
    private static BigInteger chainId;
    private static int version;
    private static String payerPrivateKey;
    private static String payeePrivateKey;
    private static String payerAddr;
    private static String payeeAddr;
    private static CITAj service;
    private static long quota;
    private static String value;
    private static Token token;
    private static Transaction.CryptoTx cryptoTx;

    static {

        Config conf = new Config();
        conf.buildService(false);

        payerPrivateKey = conf.adminPrivateKey;
        payeePrivateKey = conf.auxPrivKey1;
        payerAddr = conf.adminAddress;
        payeeAddr = conf.auxAddr1;
        service = conf.service;
        quota = Long.parseLong(conf.defaultQuotaDeployment);
        value = "0";
        cryptoTx = Transaction.CryptoTx.valueOf(conf.cryptoTx);
        chainId = TestUtil.getChainId(service);
        version = TestUtil.getVersion(service);
    }

    private static long getBalance(String addr) {
        long accountBalance = 0;
        try {
            Future<BigInteger> balanceFuture =
                    token.getBalance(addr).sendAsync();
            accountBalance = balanceFuture.get(8, TimeUnit.SECONDS).longValue();
        } catch (Exception e) {
            System.out.println("Failed to get balance of account: " + addr);
            e.printStackTrace();
            //System.exit(1);
        }
        return accountBalance;
    }

    private void eventObserve() {
        Flowable<Token.TransferEventResponse> flowable =
                token.transferEventFlowable(
                        DefaultBlockParameter.valueOf(BigInteger.ONE),
                        DefaultBlockParameterName.PENDING);
        flowable.subscribe(new Consumer<Token.TransferEventResponse>() {
            @Override
            public void accept(Token.TransferEventResponse event) throws Exception {
                System.out.println(
                        "Flowable, TransferEvent(" + event._from + ", "
                                + event._to + ", " + event._value.longValue() + ")");
            }
        });
    }

    private void randomTransferToken() {
        new Thread(this::eventObserve).start();

        Credentials fromCredential = Credentials.create(payerPrivateKey);
        Credentials toCredential = Credentials.create(payeePrivateKey);

        for (int i = 0; i < 6; i++) {
            System.out.println("Transfer " + i);
            long fromBalance = getBalance(payerAddr);
            long transferAmount = ThreadLocalRandom
                    .current().nextLong(0, fromBalance);
            TransferEvent event = new TransferEvent(payerAddr, payeeAddr, transferAmount);
            System.out.println("Transaction " + event.toString() + " is being executing.");
            event.execute();
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (Exception e) {
                System.out.println("Thread interrupted.");
            }
            long fromBalanceNow = getBalance(payerAddr);
            assertThat(fromBalance-fromBalanceNow,equalTo(transferAmount));
        }
    }


    @Test
    public void TokenFilterCodeGenTest() throws Exception {
        TransactionManager rawTransactionManager = new RawTransactionManager(
                service, Credentials.create(payerPrivateKey), 5, 3000);
        //Instantiate SM2 style rawTransactionManager
        if (cryptoTx == Transaction.CryptoTx.SM2) {
            SM2 sm2 = new SM2();
            rawTransactionManager =  RawTransactionManager.createSM2Manager(
                    service, sm2.fromPrivateKey(payerPrivateKey), 5, 3000);
        }

        long validUtilBlock = TestUtil.getValidUtilBlock(service).longValue();
        String nonce = TestUtil.getNonce();

        Future<Token> tokenFuture = Token.deploy(
                service, rawTransactionManager, 1000000L, nonce,
                validUtilBlock, version,
                value, chainId).sendAsync();
        TokenFilterCodeGenTest tokenFilterCodeGenExample = new TokenFilterCodeGenTest();

        System.out.println("Wait 10s for contract to be deployed...");
        Thread.sleep(10000);

        token = tokenFuture.get();
        assertNotNull(token.getContractAddress());
        System.out.println("contract deployment success. Contract address: "
                + token.getContractAddress());

        //in cita 0.20, it seems that contract is not ready even if address is returned.

        System.out.println("waiting for transaction in the block");
        TimeUnit.SECONDS.sleep(4);
        System.out.println("interrupted when waiting for transactions written into block");

        System.out.println("Contract initial state: ");
        tokenFilterCodeGenExample.randomTransferToken();

    }

    private class TransferEvent {
        String from;
        String to;
        long tokens;

        TransferEvent(String from, String to, long tokens) {
            this.from = from;
            this.to = to;
            this.tokens = tokens;
        }

        void execute() {
            Token tokenContract = TokenFilterCodeGenTest.this.token;
            long validUtilBlock = TestUtil.getValidUtilBlock(
                    TokenFilterCodeGenTest.this.service).longValue();

            String nonce = TestUtil.getNonce();
            tokenContract.transfer(
                    this.to, BigInteger.valueOf(tokens),
                    TokenFilterCodeGenTest.quota,
                    nonce, validUtilBlock, version, chainId, value).sendAsync();
        }

        @Override
        public String toString() {
            return "TransferEvent(" + this.from
                    + ", " + this.to + ", " + this.tokens + ")";
        }
    }
}
