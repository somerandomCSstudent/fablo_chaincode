package hu.bme.mit.ftsrg.chaincode.assettransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CarBusinessObject {
    private final String ID;
    private final String Brand;
    private final String Model;
    private final String Color;
    private final String Owner;
    private final String MaintainedBy;
}