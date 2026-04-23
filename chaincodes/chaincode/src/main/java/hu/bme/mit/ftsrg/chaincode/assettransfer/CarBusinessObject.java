package hu.bme.mit.ftsrg.chaincode.assettransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@Sharding(shardingClass = CarSharding.class)
public class CarBusinessObject {
    private final String idNum;
    private final String brand;
    private final String model;
    private final String color;
    private final String owner;
    private final String maintainedBy;
}