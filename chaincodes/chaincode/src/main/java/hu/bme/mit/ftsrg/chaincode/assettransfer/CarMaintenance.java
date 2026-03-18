package hu.bme.mit.ftsrg.chaincode.assettransfer;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder(toBuilder = true)
@Jacksonized
public class CarMaintenance {
    private final String ID;
    private final String MaintainedBy;

    public String ID() {
        return ID;
    }
}
