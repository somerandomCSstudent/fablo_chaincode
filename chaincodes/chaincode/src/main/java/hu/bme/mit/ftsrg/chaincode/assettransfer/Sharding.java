package hu.bme.mit.ftsrg.chaincode.assettransfer;

public @interface Sharding {
    public Class<? extends ShardingDefinition> shardingClass();
}
