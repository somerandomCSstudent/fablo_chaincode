package hu.bme.mit.ftsrg.chaincode.assettransfer;

import java.util.List;

public interface ShardingDefintion {

    List<List<String>> getShards();
}
