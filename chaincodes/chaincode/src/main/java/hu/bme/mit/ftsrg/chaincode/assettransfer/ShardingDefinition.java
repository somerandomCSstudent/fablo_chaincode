package hu.bme.mit.ftsrg.chaincode.assettransfer;

import java.util.List;

public interface ShardingDefinition {

    List<List<String>> getShards();
}
