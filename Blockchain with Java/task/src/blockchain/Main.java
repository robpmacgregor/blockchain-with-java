package blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class ProofOfWork implements Runnable{
    Block block;
    private long minimumMagicNumber = -1;
    private long maximumMagicNumber = -1;

    public ProofOfWork(Block block) {
        this.block = block;

    }

    public void setMinimumMagicNumber(long minimumMagicNumber) {
        this.minimumMagicNumber = minimumMagicNumber;
    }

    public void setMaximumMagicNumber(long maximumMagicNumber) {
        this.maximumMagicNumber = maximumMagicNumber;
    }

    @Override
    public void run() throws RuntimeException {
        if (minimumMagicNumber == -1 || maximumMagicNumber == -1) {
            throw new RuntimeException("magic number range not set");
        }

        HashString tempHash;
        long magicNumber;
        final long minimumMagicNumber = this.minimumMagicNumber;
        final long maximumMagicNumber = this.maximumMagicNumber;

        while (block.getHash() == null) {
            magicNumber = ThreadLocalRandom.current().nextLong(minimumMagicNumber, maximumMagicNumber);
            tempHash = new HashString(String.format(
                    "%d%d%s%d",
                    block.getId(),
                    block.getCreatedAt(),
                    block.getPreviousBlockHash(),
                    magicNumber));

            if (tempHash.isProven()) {
                block.setHash(tempHash);
                block.setMagicNumber(magicNumber);
            }
        }
    }
}

class Block {

    private final HashString previousBlockHash;
    private final int id;
    private final long created_at;
    private volatile HashString hash = null;
    private long magicNumber;
    private final int processingTime;

    public Block(HashString previousBlockHash, int id) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        this.previousBlockHash = previousBlockHash;
        this.id = id;
        this.created_at = new Date().getTime();

        ProofOfWork proofOfWork = new ProofOfWork(this);
        int threadCount = 8;
        Thread[] threads = new Thread[threadCount];

        long n = 100_000_000_000_000L;
        long min = 0;
        long max;
        for (int i = 0; i < threads.length; i++) {
            max = min + (n / threadCount) -1;

            proofOfWork.setMinimumMagicNumber(min);
            proofOfWork.setMaximumMagicNumber(max);

            min = max+1;

            threads[i] = new Thread(proofOfWork, "PoW-" + i);
            threads[i].start();
            Thread.sleep(10);
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        long endTime = System.currentTimeMillis();
        this.processingTime = (int) (endTime - startTime) / 1000;

    }

    public HashString getPreviousBlockHash() {
        return previousBlockHash;
    }

    public int getId() {
        return id;
    }

    public HashString getHash() {
        return hash;
    }

    public long getCreatedAt() {
        return created_at;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(long magicNumber) {
        this.magicNumber = magicNumber;
    }

    public void setHash(HashString hash) {
        this.hash = hash;
    }

    @Override
    public String toString() {
        return String.format(
            "Block:%n" +
            "Id: %d%n" +
            "Timestamp: %d%n" +
            "Magic number: %d%n" +
            "Hash of the previous block:%n" +
            "%s%n" +
            "Hash of the block:%n" +
            "%s%n" +
            "Block was generating for %d seconds%n",
            this.id,
            this.created_at,
            this.magicNumber,
            this.previousBlockHash,
            this.hash,
            this.processingTime
        );
    }
}
class InvalidBlockException extends RuntimeException{
    public InvalidBlockException(String message) {
        super(message);
    }
}

class Blockchain {
    private final ArrayList<Block> blockchain = new ArrayList<>();

    public void generateNewBlock() {
        HashString previousBlockHash = new HashString("");
        int id = 1;

        if (!blockchain.isEmpty()) {
            Block previousBlock = blockchain.getLast();
            previousBlockHash = previousBlock.getHash();
            id = previousBlock.getId() + 1;
        }

        try{
            Block newBlock = new Block(previousBlockHash, id);
            blockchain.add(newBlock);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    public void validateBlockChain() throws InvalidBlockException {

        blockchain.forEach(block -> {
            HashString hashString = new HashString(String.format("%d%d%s%d", block.getId(), block.getCreatedAt(), block.getPreviousBlockHash(), block.getMagicNumber()));

            if (!block.getHash().toString().equals(hashString.toString())) {
                throw new InvalidBlockException(String.format("Hash %s is invalid for block id %d", block.getHash(), block.getId()));
            }
        });
    }

    public ArrayList<Block> getBlockchain() {
        return blockchain;
    }
}

class HashString {
    private String hashString;
    public static String prefix = "000000";
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

    public boolean isProven() {
        return (hashString.startsWith(prefix));
    }
}

public class Main {

    public static final int NUMBER_OF_BLOCKS = 5;

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter how many zeros the hash must start with: ");
        int prefixLength = scanner.nextInt();
        System.out.println();

        char[] c = new char[prefixLength];
        Arrays.fill(c, '0');
        HashString.prefix = String.valueOf(c);

        Blockchain blockchain = new Blockchain();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            blockchain.generateNewBlock();
        }

        try{
            blockchain.validateBlockChain();
        } catch (InvalidBlockException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        ArrayList<Block> blocks = blockchain.getBlockchain();

        blocks.forEach(System.out::println);
    }
}
