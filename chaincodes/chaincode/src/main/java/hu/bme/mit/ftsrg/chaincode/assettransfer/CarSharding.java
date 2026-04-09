package hu.bme.mit.ftsrg.chaincode.assettransfer;

import java.util.List;

public class CarSharding {
    public static List<List<String>> getShards() {
        return List.of(
                List.of("Brand", "Model"), // Shard 0: Identity
                List.of("Color", "Owner"), // Shard 1: Properties/Ownership
                List.of("MaintainedBy") // Shard 2: Maintenance
        );
    }
}