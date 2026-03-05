package hu.bme.mit.ftsrg.chaincode.assettransfer;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder(toBuilder = true)
@Jacksonized
public class Car {
    private final String ID;
    private final String Make;
    private final String Model;
    private final String Color;
    private final String Owner;
    private final int Mileage;

    // These manual getters are needed because your code calls car.ID() and
    // car.Owner()
    public String ID() {
        return ID;
    }

    public String Owner() {
        return Owner;
    }
}