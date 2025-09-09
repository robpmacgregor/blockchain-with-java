package blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
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
    private final String[] payload;


    private Block(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds,
            String[] payload
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
            String[] payload
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

    public String[] getPayload() {
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
    private String[] nextBlockPayload;

    public static int MAX_LENGTH = 5;

    private final AtomicInteger nextBlockId = new AtomicInteger(1);

    private final Object lock = new Object();

    private final Object setPayloadLock = new Object();

    private Blockchain(BlockValidator validator) {
        this.validator = validator;
    }

    public static Blockchain create() {
        return new Blockchain(new BlockValidator());
    }

    public void addBlock(Block block, PostAddAction postAddAction) throws InvalidBlockException{
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

    public synchronized String[] getNextBlockPayload() {
        return nextBlockPayload;
    }

    public void setNextBlockPayload(String[] nextBlockPayload) {
        synchronized (setPayloadLock) {
            this.nextBlockPayload = nextBlockPayload;
        }
    }
}

class BlockValidator {

    public boolean isValid(Block previousBlock, Block newBlock){
        if (newBlock.getId() != previousBlock.getId()+1) {
            return false;
        }

        HashString hash = new HashString(String.format(
                "%s%d%s%d%s",
                newBlock.getId(),
                newBlock.getCreatedAt(),
                previousBlock.getHash(),
                newBlock.getMagicNumber(),
                String.join(" ", newBlock.getPayload())
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
    public void act(Block block);
}

@FunctionalInterface
interface PostAddAction {
    public void act();
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

    public void mineBlock(ChatServer chatServer, PostMineAction postMineAction) {
        int localBlockchainLength = blockchain.getLength();
        while (true) {
            int id = blockchain.getNextBlockId();
            HashString previousBlockHash = (blockchain.getLength() > 0) ? blockchain.getLastBlock().getHash() : new HashString("");
            long createdAt = new Date().getTime();
            long startTime = System.currentTimeMillis();
            Optional<HashString> optHash = Optional.empty();
            long magicNumber = 0;
            String[] payload = new String[0];
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

                optHash = Optional.of(new HashString(String.format(
                        "%s%d%s%d%s",
                        id,
                        createdAt,
                        previousBlockHash,
                        magicNumber,
                        String.join(" ", payload)
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
            String[] payload) {
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
                        "Block data:" + (block.getPayload().length > 0 ? "%n" : " ") +
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
                (block.getPayload().length > 0) ? String.join("\n", block.getPayload()) : "no messages",
                block.getCreationTimeInSeconds(),
                proofOfWorkComplexity
        );
    }
}

class User {
    private final String name;

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static User create(String name) {
        return new User(name);
    }
}

class ChatMessage {
    User user;
    String message;

    public ChatMessage(User user, String message) {
        this.user = user;
        this.message = message;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public static ChatMessage create(User user, String message) {
        return new ChatMessage(user, message);
    }

    public String toString() {
        return String.format("%s: %s", user.getName(), message);
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

    public ChatClient(User user, ChatServer server) {
        this.user = user;
        this.server = server;
    }

    public void send(String message) throws InterruptedException {
        server.addMessage(ChatMessage.create(user, message));
    }

    public static ChatClient create(User user, ChatServer server) {
        return new ChatClient(user, server);
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
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage());
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
        miner.mineBlock(chatServer, block -> {
            blockchain.addBlock(block, () -> {
                blockchain.incrementProofOfWorkComplexity();
                BlockChainPrinter.print(block, blockchain.getProofOfWorkComplexity());
                blockchain.setNextBlockPayload(
                        chatServer
                                .getAllMessagesAndClear()
                                .stream()
                                .map(ChatMessage::toString)
                                .toArray(String[]::new)
                );
            });

        });
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
                        User user = User.create(entry.getKey());
                        ChatClient chatClient = ChatClient.create(user, chatServer);
                        ChatClientRunnable chatClientRunnable = new ChatClientRunnable(chatClient, entry.getValue());
                        executorService.execute(chatClientRunnable);
                    });

        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }
}
