package hu.bme.mit.ftsrg.chaincode.assettransfer;

import java.util.List;

public class PhoneSharding implements ShardingDefintion {
    public List<List<String>> getShards() {
        return List.of(
                List.of("Brand", "Model"),
                List.of("Color", "Owner"),
                List.of("SoC", "Memory"),
                List.of("MaintainedBy"));
    }

}
