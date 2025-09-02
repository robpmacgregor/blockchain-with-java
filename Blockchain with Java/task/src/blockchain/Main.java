package blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private Block(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds
            ) {
        this.hash = hash;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.id = id;
        this.previousBlockHash = previousBlockHash;
        this.magicNumber = magicNumber;
        this.creationTimeInSeconds = creationTimeInSeconds;
    }

    public static Block create(
            int id,
            long createdAt,
            int createdBy,
            HashString previousBlockHash,
            HashString hash,
            long magicNumber,
            long creationTimeInSeconds) {
        return new Block(id, createdAt, createdBy, previousBlockHash, hash, magicNumber, creationTimeInSeconds);
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
}

class InvalidBlockException extends RuntimeException{
    public InvalidBlockException(String message) {
        super(message);
    }

}class BlockNotFoundException extends RuntimeException{
    public BlockNotFoundException(String message) {
        super(message);
    }
}

class Blockchain {
    private volatile ArrayList<Block> blockchain = new ArrayList<>();
    private final BlockValidator validator;
    private AtomicInteger proofOfWorkComplexity = new AtomicInteger(0);

    public static int MAX_LENGTH = 5;

    private final AtomicInteger nextBlockId = new AtomicInteger(1);

    private final Object lock = new Object();

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
}

class BlockValidator {

    public boolean isValid(Block previousBlock, Block newBlock){
        if (newBlock.getId() != previousBlock.getId()+1) {
            return false;
        }

        HashString hash = new HashString(String.format(
                "%s%d%s%d",
                newBlock.getId(),
                newBlock.getCreatedAt(),
                previousBlock.getHash(),
                newBlock.getMagicNumber()));

        if (!newBlock.getHash().toString().equals(hash.toString())) {
            return false;
        }
        return true;
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

    public void mineBlock(PostMineAction postMineAction) {
        int localBlockchainLength = blockchain.getLength();
        while (true) {
            int id = blockchain.getNextBlockId();
            HashString previousBlockHash = (blockchain.getLength() > 0) ? blockchain.getLastBlock().getHash() : new HashString("");
            long createdAt = new Date().getTime();
            long startTime = System.currentTimeMillis();
            Optional<HashString> optHash = Optional.empty();
            long magicNumber = 0;
            while (optHash.isEmpty() || !proveHash(optHash.get())) {
                if (blockchain.getLength() == Blockchain.MAX_LENGTH) {
                    return;
                }
                if (localBlockchainLength < blockchain.getLength()) {
                    id = blockchain.getNextBlockId();
                    previousBlockHash = blockchain.getLastBlock().getHash();
                    createdAt = new Date().getTime();
                    startTime = System.currentTimeMillis();
                    localBlockchainLength = blockchain.getLength();
                }
                magicNumber = ThreadLocalRandom.current().nextLong(0, 10000000000L);

                optHash = Optional.of(new HashString(String.format(
                        "%s%d%s%d",
                        id,
                        createdAt,
                        previousBlockHash,
                        magicNumber)));
            }
            long endTime = System.currentTimeMillis();
            long creationTimeInSeconds = (endTime - startTime) / 1000;
            Block block = createNewBlock(id, createdAt, minerId, previousBlockHash, optHash.get(), magicNumber, creationTimeInSeconds);
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
            long creationTimeInSeconds) {
        return Block.create(id, createdAt, createdBy, previousBlockHash, hash, magicNumber, creationTimeInSeconds);
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
                        "Block was generating for %d seconds%n" +
                        "N was increased to %d%n" +
                        "%n",
                block.getCreatedBy(),
                block.getId(),
                block.getCreatedAt(),
                block.getMagicNumber(),
                block.getPreviousBlockHash(),
                block.getHash(),
                block.getCreationTimeInSeconds(),
                proofOfWorkComplexity
        );
    }
}

public class Main {
    private static final int NUMBER_OF_MINERS = 2;

    public static void main(String[] args) {
        Blockchain blockchain = Blockchain.create();
        try(ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_MINERS)) {
            for (int i = 0; i < NUMBER_OF_MINERS; i++) {
                Miner miner = Miner.create(blockchain);
                executorService.execute(()->{
                    miner.mineBlock(block -> {
                        blockchain.addBlock(block, () -> {
                            blockchain.incrementProofOfWorkComplexity();
                            BlockChainPrinter.print(block, blockchain.getProofOfWorkComplexity());
                        });

                    });
                });
            }
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
        }
    }
}
