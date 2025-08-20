package blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

class Block {

    private HashString previousBlockHash;
    private int id;
    private long created_at;
    private HashString hash;

    public Block(HashString previousBlockHash, int id) {
        this.previousBlockHash = previousBlockHash;
        this.id = id;
        this.created_at = new Date().getTime();
        this.hash = new HashString(String.format("%d%d", id, created_at));
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

    @Override
    public String toString() {
        return String.format(
            "Block:%n" +
            "Id: %d%n" +
            "Timestamp: %d%n" +
            "Hash of the previous block:%n" +
            "%s%n" +
            "Hash of the block:%n" +
             "%s%n",
            this.id,
            this.created_at,
            this.previousBlockHash,
            this.hash
        );
    }
}
class InvalidBlockException extends RuntimeException{
    public InvalidBlockException(String message) {
        super(message);
    }
}

class Blockchain {
    private ArrayList<Block> blockchain = new ArrayList<>();

    public void generateNewBlock() {
        HashString previousBlockHash = new HashString("");
        int id = 1;

        if (!blockchain.isEmpty()) {
            Block previousBlock = blockchain.getLast();
            previousBlockHash = previousBlock.getHash();
            id = previousBlock.getId() + 1;
        }

        Block newBlock = new Block(previousBlockHash, id);
        blockchain.add(newBlock);
    }

    public void validateBlockChain() throws InvalidBlockException {

        blockchain.forEach(block -> {
            HashString hashString = new HashString(String.format("%d%d", block.getId(), block.getCreatedAt()));

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

    public HashString(String string) {
        this.hashString = "0";
        if (!string.isEmpty()) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

                byte[] encodedHash = messageDigest.digest(
                    string.getBytes(StandardCharsets.UTF_8)
                );
                this.hashString = HexFormat.of().formatHex(encodedHash);
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

public class Main {

    public static final int NUMBER_OF_BLOCKS = 5;

    public static void main(String[] args) {

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
