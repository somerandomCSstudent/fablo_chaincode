package hu.bme.mit.ftsrg.chaincode.assettransfer;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder(toBuilder = true)
@Jacksonized
public class CarEntityMain {
    private final String ID;
    private final String Brand;
    private final String Model;
    private final String Color;
    private final String Owner;
}