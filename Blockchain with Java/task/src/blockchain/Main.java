package blockchain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class Block {

    private final HashString previousBlockHash;
    private final int id;
    private final long createdAt;
    private final int createdBy;
    private final HashString hash;
    private final long magicNumber;
    private final long creationTimeInSeconds;
    private static int nextId = 1;
    private final Transaction[] payload;


    private Block(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds,
            Transaction[] payload
    ) {
        this.hash = hash;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.id = id;
        this.previousBlockHash = previousBlockHash;
        this.magicNumber = magicNumber;
        this.creationTimeInSeconds = creationTimeInSeconds;
        this.payload = payload;
    }

    public static Block create(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds,
            Transaction[] payload
    ) {
        return new Block(id, createdAt, createdBy, previousBlockHash, hash, magicNumber, creationTimeInSeconds, payload);
    }

    public synchronized static int getNextId() {
        int id = nextId;
        nextId++;
        return id;
    }

    public int getId() {
        return id;
    }

    public HashString getHash() {
        return hash;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public HashString getPreviousBlockHash() {
        return previousBlockHash;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public long getCreationTimeInSeconds() {
        return creationTimeInSeconds;
    }

    public Transaction[] getPayload() {
        return payload;
    }
}

class InvalidBlockException extends RuntimeException{
    public InvalidBlockException(String message) {
        super(message);
    }

}

class BlockNotFoundException extends RuntimeException{
    public BlockNotFoundException(String message) {
        super(message);
    }
}

class Blockchain {
    private volatile ArrayList<Block> blockchain = new ArrayList<>();
    private final BlockValidator validator;
    private AtomicInteger proofOfWorkComplexity = new AtomicInteger(0);
    private Transaction[] nextBlockPayload;

    public static int MAX_LENGTH = 5;

    private final AtomicInteger nextBlockId = new AtomicInteger(1);

    private final Object lock = new Object();

    private final Object setPayloadLock = new Object();

    public Blockchain(BlockValidator validator) {
        this.validator = validator;
    }

    public static Blockchain create() throws NoSuchAlgorithmException {

        return new Blockchain(
                new BlockValidator(
                        new MessageVerifier()
                )
        );
    }

    public void addBlock(Block block, PostAddAction postAddAction) throws InvalidBlockException, NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException, InterruptedException {
        synchronized (lock) {
            if (getLength() >= Blockchain.MAX_LENGTH) {
                return;
            }

            if (getLength() > 0 && !validator.isValid(getLastBlock(), block)) {
                System.out.println("Block Id " + block.getId() + " is not valid");
                return;
            }

            blockchain.add(block);
            incrementNextBlockId();
            postAddAction.act();
        }
    }

    public Block getBlock(int id) {
        return (Block) blockchain.stream().filter(b -> b.getId() == id);

    }

    public Block getLastBlock() {
        if (blockchain.isEmpty()) {
            throw new BlockNotFoundException("Can't get last block, chain is empty");
        }
        return blockchain.getLast();
    }

    public ArrayList<Block> getAllBlocks() {
        return blockchain;
    }

    public int getProofOfWorkComplexity() {
        return proofOfWorkComplexity.get();
    }

    public void incrementProofOfWorkComplexity() {
        proofOfWorkComplexity.incrementAndGet();
    }

    public int getLength() {
        synchronized (lock) {
            return blockchain.size();
        }
    }

    public int getNextBlockId() {
        return nextBlockId.get();
    }

    public void incrementNextBlockId() {
        nextBlockId.incrementAndGet();
    }

    public synchronized Transaction[] getNextBlockPayload() {
        return nextBlockPayload;
    }

    public void setNextBlockPayload(Transaction[] nextBlockPayload) {
        synchronized (setPayloadLock) {
            this.nextBlockPayload = nextBlockPayload;
        }
    }
}

class BlockValidator {
    MessageVerifier messageVerifier;

    public BlockValidator(MessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
    }

    public boolean isValid(Block previousBlock, Block newBlock) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        if (newBlock.getId() != previousBlock.getId()+1) {
            return false;
        }
        Transaction[] transactions = newBlock.getPayload();

        for (Transaction transaction : transactions) {
            if (!messageVerifier.verify(transaction.toString(), transaction.getSignature(), transaction.getPublicKey())) {
                return false;
            }
        }

        if ((newBlock.getPayload().length > 0 && previousBlock.getPayload().length > 0)
                && newBlock.getPayload()[0].getId() <= previousBlock.getPayload()[previousBlock.getPayload().length -1].getId()) {
            return false;
        }

        String payload = Arrays.stream(newBlock.getPayload())
                .map(Transaction::toString)
                .reduce("", (acc, next) -> acc + " " + next);

        HashString hash = new HashString(String.format(
                "%s%d%s%d%s",
                newBlock.getId(),
                newBlock.getCreatedAt(),
                previousBlock.getHash(),
                newBlock.getMagicNumber(),
                payload
        ));
        return newBlock.getHash().toString().equals(hash.toString());
    }
}

class HashString {
    private String hashString;
    public HashString(String string) {
        this.hashString = "0";

        if (!string.isEmpty()) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                byte[] encodedHash = messageDigest.digest(
                        string.getBytes(StandardCharsets.UTF_8)
                );
                hashString = HexFormat.of().formatHex(encodedHash);
            } catch (NoSuchAlgorithmException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return hashString;
    }
}

@FunctionalInterface
interface PostMineAction {
    public void act(Block block) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException, InterruptedException;
}

@FunctionalInterface
interface PostAddAction {
    public void act() throws InterruptedException;
}

class Miner {
    private final int minerId;
    private final Blockchain blockchain;
    private static int nextId = 1;
    private User user;

    private Miner(Blockchain blockchain) {
        this.minerId = getNextId();
        this.blockchain = blockchain;
    }
    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getMinerId() {
        return minerId;
    }

    private int getNextId() {
        int id = nextId;
        nextId++;
        return id;
    }
    public static Miner create (Blockchain blockchain) {
        return new Miner(blockchain);
    }

    public void mineBlock(TransactionServer transactionServer, TransactionFactory transactionFactory, PostMineAction postMineAction) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException, InterruptedException {
        int localBlockchainLength = blockchain.getLength();
        while (true) {
            int id = blockchain.getNextBlockId();
            HashString previousBlockHash = (blockchain.getLength() > 0) ? blockchain.getLastBlock().getHash() : new HashString("");
            long createdAt = new Date().getTime();
            long startTime = System.currentTimeMillis();
            Optional<HashString> optHash = Optional.empty();
            long magicNumber = 0;
            Transaction[] payload = new Transaction[0];
            while (optHash.isEmpty() || !proveHash(optHash.get())) {
                if (blockchain.getLength() == Blockchain.MAX_LENGTH) {
                    return;
                }
                if (localBlockchainLength < blockchain.getLength()) {
                    id = blockchain.getNextBlockId();
                    previousBlockHash = blockchain.getLastBlock().getHash();
                    createdAt = new Date().getTime();
                    startTime = System.currentTimeMillis();
                    ArrayList<Transaction> payloadArray = new ArrayList<>();

                    //TO DO: implement a non-blocking wait for next payload
                    while(payloadArray.isEmpty()) {
                       payloadArray.addAll(Arrays.stream(blockchain.getNextBlockPayload()).toList());
                    }
                    payloadArray.add(transactionFactory.create(getUser(), getUser(), 100));
                    payload = payloadArray.toArray(Transaction[]::new);
                    localBlockchainLength = blockchain.getLength();
                }
                magicNumber = ThreadLocalRandom.current().nextLong(0, 10000000000L);

                String payloadString = Arrays.stream(payload)
                        .map(Transaction::toString)
                        .reduce("", (acc, next) -> acc + " " + next);

                optHash = Optional.of(new HashString(String.format(
                        "%s%d%s%d%s",
                        id,
                        createdAt,
                        previousBlockHash,
                        magicNumber,
                        payloadString
                )));
            }
            long endTime = System.currentTimeMillis();
            long creationTimeInSeconds = (endTime - startTime) / 1000;
            Block block = createNewBlock(
                    id,
                    createdAt,
                    minerId,
                    previousBlockHash,
                    optHash.get(),
                    magicNumber,
                    creationTimeInSeconds,
                    payload
            );
            postMineAction.act(block);
        }
    }

    private boolean proveHash(HashString hash) {
        char[] chars = new char[blockchain.getProofOfWorkComplexity()];
        Arrays.fill(chars, '0');
        return hash.toString().startsWith(String.valueOf(chars));
    }

    private Block createNewBlock(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds,
            Transaction[] payload) {
        return Block.create(
                id,
                createdAt,
                createdBy,
                previousBlockHash,
                hash,
                magicNumber,
                creationTimeInSeconds,
                payload
        );
    }
}

class BlockChainPrinter{
    public static void print(Block block, int proofOfWorkComplexity) {

        String payloadString = Arrays.stream(block.getPayload())
                .map(Transaction::toString)
                .reduce("", (acc, next) -> acc + next + "\n");

        System.out.printf(
                "Block:%n" +
                        "Created by miner # %s%n" +
                        "Id: %s%n" +
                        "Timestamp: %d%n" +
                        "Magic number: %d%n" +
                        "Hash of the previous block:%n" +
                        "%s%n" +
                        "Hash of the block:%n" +
                        "%s%n" +
                        "Block data:" + (!payloadString.isEmpty() ? "%n" : " no messages") +
                        "%s%n" +
                        "Block was generating for %d seconds%n" +
                        "N was increased to %d%n" +
                        "%n",
                block.getCreatedBy(),
                block.getId(),
                block.getCreatedAt(),
                block.getMagicNumber(),
                block.getPreviousBlockHash(),
                block.getHash(),
                payloadString,
                block.getCreationTimeInSeconds(),
                proofOfWorkComplexity
        );
    }
}

class User {
    private final String name;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public static User create(String name) {
        return new User(name);
    }
}

class RSAKeysGenerator {
    private KeyPairGenerator keyGen;
    private KeyPair pair;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public RSAKeysGenerator(int keyLength) throws NoSuchAlgorithmException {
        this.keyGen = KeyPairGenerator.getInstance("RSA");
        this.keyGen.initialize(keyLength);
        this.pair = this.keyGen.generateKeyPair();
        this.privateKey = this.pair.getPrivate();
        this.publicKey = this.pair.getPublic();
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}

class MessageSigner {
    private final PrivateKey privateKey;

    public MessageSigner(PrivateKey privateKey) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        this.privateKey = privateKey;
    }

    public byte[] sign(String message) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        Signature rsa = Signature.getInstance("SHA1withRSA");
        rsa.initSign(privateKey);
        rsa.update(message.getBytes());
        return rsa.sign();
    }
}

class MessageVerifier {
    public boolean verify(String message, byte[] signature, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initVerify(publicKey);
        sig.update(message.getBytes());
        return sig.verify(signature);
    }
}

class TransactionFactory {
    MessageSigner messageSigner;
    MessageVerifier messageVerifier;

    public TransactionFactory(MessageSigner messageSigner, MessageVerifier messageVerifier) {
        this.messageSigner = messageSigner;
        this.messageVerifier = messageVerifier;
    }

    public Transaction create(User sourceUser, User destinationUser, int value) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        Transaction transaction = new Transaction(sourceUser, destinationUser, value);
        byte[] signature =  messageSigner.sign(transaction.toString());
        PublicKey publicKey = sourceUser.getPublicKey();
        transaction.setSignature(signature);
        transaction.setPublicKey(publicKey);
        return transaction;
    }
}

class Transaction {
    private static int nextId = 1;
    private int id;
    private User sourceUser;
    private User destinationUser;
    private int value;
    private byte[] signature;
    private PublicKey publicKey;

    public  static int getNextId() {
        return nextId++;
    }

    public Transaction(User sourceUser, User destimationUser, int value) {
        this.id = getNextId();
        this.sourceUser = sourceUser;
        this.destinationUser = destimationUser;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }

    public User getDestinationUser() {
        return destinationUser;
    }

    public User getSourceUser() {
        return sourceUser;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public static Transaction create(User sourceUser, User destinationUser, int value) {
        return new Transaction(sourceUser, destinationUser, value);
    }

    public String toString() {
        return String.format("%s sent %d VC to %s", sourceUser, value, destinationUser);
    }
}

class TransactionServer {
    private final ArrayList<Transaction> transactions = new ArrayList<>();
    private final Object lock = new Object();

    public synchronized void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public  ArrayList<Transaction> getAllTransactionsAndClear() {
        synchronized (lock) {
            ArrayList<Transaction> tempTransactionList = new ArrayList<Transaction>(transactions);
            transactions.clear();
            return tempTransactionList;
        }
    }

    public static TransactionServer create() {
        return new TransactionServer();
    }
}

class TransactionClient {
    private final User user;
    private final TransactionServer server;
    private final TransactionFactory transactionFactory;

    public TransactionClient(User user, TransactionServer server, TransactionFactory transactionFactory) {
        this.user = user;
        this.server = server;
        this.transactionFactory = transactionFactory;
    }

    public void send(User destinationUser, int value) throws InterruptedException, NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        Transaction transaction = transactionFactory.create(user, destinationUser, value);
        server.addTransaction(transaction);
    }

    public static TransactionClient create(User user, TransactionServer server, TransactionFactory transactionFactory) {
        return new TransactionClient(user, server, transactionFactory);
    }
}

class TransactionClientRunnable implements Runnable {
    TransactionClient transactionClient;
    List<Integer> transactions;

    public TransactionClientRunnable(TransactionClient transactionClient, List<Integer> transactions) {
        this.transactionClient = transactionClient;
        this.transactions = transactions;
    }

    @Override
    public void run() {
        transactions.forEach(m -> {
            try {
                transactionClient.send(new User("Morek"), m);
                Thread.sleep(50);

            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException | SignatureException |
                     InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

class MinerRunnable implements Runnable {
    Miner miner;
    TransactionServer transactionServer;
    Blockchain blockchain;
    private TransactionFactory transactionFactory;

    public MinerRunnable(Miner miner, TransactionServer transactionServer, Blockchain blockchain, TransactionFactory transactionFactory) {
        this.miner = miner;
        this.transactionServer = transactionServer;
        this.blockchain = blockchain;
        this.transactionFactory = transactionFactory;
    }

    @Override
    public void run() {
        try {
            miner.mineBlock(transactionServer, transactionFactory, block -> {
                blockchain.addBlock(block, () -> {
                    blockchain.incrementProofOfWorkComplexity();
                    BlockChainPrinter.print(block, blockchain.getProofOfWorkComplexity());
                    blockchain.setNextBlockPayload(
                            transactionServer.getAllTransactionsAndClear().toArray(Transaction[]::new)
                    );
                });
            });
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException | SignatureException |
                 InvalidKeyException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

public class Main {
    private static final int NUMBER_OF_MINERS = 2;
    private static final Map<String, List<Integer>> USERS = Map.of(
            "Tom", List.of(10, 20, 30),
            "Alice", List.of(45, 5, 34)
    );



    public static void main(String[] args) throws NoSuchAlgorithmException {

        Blockchain blockchain = Blockchain.create();
        TransactionServer transactionServer = TransactionServer.create();

        try (ExecutorService executorService = Executors.newFixedThreadPool(USERS.size() + NUMBER_OF_MINERS)) {
            for (int i = 0; i < NUMBER_OF_MINERS; i++) {
                Miner miner = Miner.create(blockchain);

                User user = User.create("miner-"+ miner.getMinerId());
                RSAKeysGenerator rsaKeysGenerator = new RSAKeysGenerator(1024);
                user.setPrivateKey(rsaKeysGenerator.getPrivateKey());
                user.setPublicKey(rsaKeysGenerator.getPublicKey());
                miner.setUser(user);
                MessageSigner messageSigner = new MessageSigner(user.getPrivateKey());
                MessageVerifier messageVerifier = new MessageVerifier();
                TransactionFactory transactionFactory = new TransactionFactory(messageSigner, messageVerifier);

                MinerRunnable minerRunnable = new MinerRunnable(miner, transactionServer, blockchain, transactionFactory);
                executorService.execute(minerRunnable);
            }
            USERS.entrySet()
                    .forEach((entry) -> {
                        try {
                            User user = User.create(entry.getKey());

                            RSAKeysGenerator rsaKeysGenerator = new RSAKeysGenerator(1024);

                            user.setPrivateKey(rsaKeysGenerator.getPrivateKey());
                            user.setPublicKey(rsaKeysGenerator.getPublicKey());
                            MessageSigner messageSigner = new MessageSigner(user.getPrivateKey());
                            MessageVerifier messageVerifier = new MessageVerifier();
                            TransactionFactory transactionFactory = new TransactionFactory(messageSigner, messageVerifier);
                            TransactionClient transactionClient = TransactionClient.create(user, transactionServer, transactionFactory);
                            TransactionClientRunnable transactionClientRunnable = new TransactionClientRunnable(transactionClient, entry.getValue());
                            executorService.execute(transactionClientRunnable);
                        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException | SignatureException |
                                 InvalidKeyException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
