package blockchain;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

class Block {

    private final HashString previousBlockHash;
    private final int id;
    private final long createdAt;
    private final int createdBy;
    private final HashString hash;
    private final long magicNumber;
    private final long creationTimeInSeconds;
    private static int nextId = 1;
    private final ChatMessage[] payload;


    private Block(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds,
            ChatMessage[] payload
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
            ChatMessage[] payload
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

    public ChatMessage[] getPayload() {
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
    private ChatMessage[] nextBlockPayload;

    public static int MAX_LENGTH = 5;

    private final AtomicInteger nextBlockId = new AtomicInteger(1);

    private final Object lock = new Object();

    private final Object setPayloadLock = new Object();

    public Blockchain(BlockValidator validator) {
        this.validator = validator;
    }

    public static Blockchain create() {
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

    public synchronized ChatMessage[] getNextBlockPayload() {
        return nextBlockPayload;
    }

    public void setNextBlockPayload(ChatMessage[] nextBlockPayload) {
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
        ChatMessage[] chatMessages = newBlock.getPayload();

        for (ChatMessage chatMessage : chatMessages) {
            if (!messageVerifier.verify(String.valueOf(chatMessage.getId()) + chatMessage.getMessage(), chatMessage.getSignature(), chatMessage.getPublicKey())) {
                return false;
            }
        }

        if ((newBlock.getPayload().length > 0 && previousBlock.getPayload().length > 0)
                && newBlock.getPayload()[0].getId() <= previousBlock.getPayload()[previousBlock.getPayload().length -1].getId()) {
            return false;
        }

        String payload = Arrays.stream(newBlock.getPayload())
                .map(ChatMessage::toString)
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

    private Miner(Blockchain blockchain) {
        this.minerId = getNextId();
        this.blockchain = blockchain;
    }

    private int getNextId() {
        int id = nextId;
        nextId++;
        return id;
    }
    public static Miner create (Blockchain blockchain) {
        return new Miner(blockchain);
    }

    public void mineBlock(ChatServer chatServer, PostMineAction postMineAction) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException, InterruptedException {
        int localBlockchainLength = blockchain.getLength();
        while (true) {
            int id = blockchain.getNextBlockId();
            HashString previousBlockHash = (blockchain.getLength() > 0) ? blockchain.getLastBlock().getHash() : new HashString("");
            long createdAt = new Date().getTime();
            long startTime = System.currentTimeMillis();
            Optional<HashString> optHash = Optional.empty();
            long magicNumber = 0;
            ChatMessage[] payload = new ChatMessage[0];
            while (optHash.isEmpty() || !proveHash(optHash.get())) {
                if (blockchain.getLength() == Blockchain.MAX_LENGTH) {
                    return;
                }
                if (localBlockchainLength < blockchain.getLength()) {
                    id = blockchain.getNextBlockId();
                    previousBlockHash = blockchain.getLastBlock().getHash();
                    createdAt = new Date().getTime();
                    startTime = System.currentTimeMillis();
                    payload = blockchain.getNextBlockPayload();
                    localBlockchainLength = blockchain.getLength();
                }
                magicNumber = ThreadLocalRandom.current().nextLong(0, 10000000000L);

                String payloadString = Arrays.stream(payload)
                        .map(ChatMessage::toString)
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
            ChatMessage[] payload) {
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
                .map(ChatMessage::toString)
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

class ChatMessageFactory {
    MessageSigner messageSigner;
    MessageVerifier messageVerifier;

    public ChatMessageFactory(MessageSigner messageSigner, MessageVerifier messageVerifier) {
        this.messageSigner = messageSigner;
        this.messageVerifier = messageVerifier;
    }

    public ChatMessage create(User user, String message) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        ChatMessage chatMessage = new ChatMessage(user, message);
        byte[] signature =  messageSigner.sign(String.valueOf(chatMessage.getId()) + chatMessage.getMessage());
        PublicKey publicKey = user.getPublicKey();
        chatMessage.setSignature(signature);
        chatMessage.setPublicKey(publicKey);
        return chatMessage;
    }
}

class ChatMessage {
    private static int nextId = 1;
    private int id;
    private User user;
    private String message;
    private byte[] signature;
    private PublicKey publicKey;

    public  static int getNextId() {
        return nextId++;
    }

    public ChatMessage(User user, String message) {
        this.id = getNextId();
        this.user = user;
        this.message = message;
    }

    public int getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
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

    public static ChatMessage create(User user, String message) {
        return new ChatMessage(user, message);
    }

    public String toString() {
        return String.format("%d - %s: %s", id, user.getName(), message);
    }
}

class ChatServer {
    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private final Object lock = new Object();

    public synchronized void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public  ArrayList<ChatMessage> getAllMessagesAndClear() {
        synchronized (lock) {
            ArrayList<ChatMessage> tempMessageList = new ArrayList<ChatMessage>(messages);
            messages.clear();
            return tempMessageList;
        }
    }

    public static ChatServer create() {
        return new ChatServer();
    }
}

class ChatClient {
    private final User user;
    private final ChatServer server;
    private final ChatMessageFactory chatMessageFactory;

    public ChatClient(User user, ChatServer server, ChatMessageFactory chatMessageFactory) {
        this.user = user;
        this.server = server;
        this.chatMessageFactory = chatMessageFactory;
    }

    public void send(String message) throws InterruptedException, NoSuchAlgorithmException, IOException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        ChatMessage chatMessage = chatMessageFactory.create(user, message);
        server.addMessage(chatMessage);
    }

    public static ChatClient create(User user, ChatServer server, ChatMessageFactory chatMessageFactory) {
        return new ChatClient(user, server, chatMessageFactory);
    }
}

class ChatClientRunnable implements Runnable {
    ChatClient chatClient;
    List<String> messages;

    public ChatClientRunnable(ChatClient chatClient, List<String> messages) {
        this.chatClient = chatClient;
        this.messages = messages;
    }

    @Override
    public void run() {
        messages.forEach(m -> {
            try {
                chatClient.send(m);
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
    ChatServer chatServer;
    Blockchain blockchain;

    public MinerRunnable(Miner miner, ChatServer chatServer, Blockchain blockchain) {
        this.miner = miner;
        this.chatServer = chatServer;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {
        try {
            miner.mineBlock(chatServer, block -> {
                blockchain.addBlock(block, () -> {
                    blockchain.incrementProofOfWorkComplexity();
                    BlockChainPrinter.print(block, blockchain.getProofOfWorkComplexity());
                    Thread.sleep(50);
                    blockchain.setNextBlockPayload(
                            chatServer.getAllMessagesAndClear().toArray(ChatMessage[]::new)
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
    private static final Map<String, List<String>> USERS = Map.of(
            "Tom", List.of("Hi Alice", "I'm good, thanks", "How about you?", "I know, it's great"),
            "Alice", List.of("Hi Tom", "How are you?", "I'm good too", "This chat is neat")
    );

    public static void main(String[] args) {
        Blockchain blockchain = Blockchain.create();
        ChatServer chatServer = ChatServer.create();

        try (ExecutorService executorService = Executors.newFixedThreadPool(USERS.size() + NUMBER_OF_MINERS)) {
            for (int i = 0; i < NUMBER_OF_MINERS; i++) {
                Miner miner = Miner.create(blockchain);
                MinerRunnable minerRunnable = new MinerRunnable(miner, chatServer, blockchain);
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
                            ChatMessageFactory chatMessageFactory = new ChatMessageFactory(messageSigner, messageVerifier);
                            ChatClient chatClient = ChatClient.create(user, chatServer, chatMessageFactory);
                            ChatClientRunnable chatClientRunnable = new ChatClientRunnable(chatClient, entry.getValue());
                            executorService.execute(chatClientRunnable);
                        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException | SignatureException |
                                 InvalidKeyException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }
}
