/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.chaincode.assettransfer;

import static hu.bme.mit.ftsrg.chaincode.assettransfer.Serializer.*;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.contract.annotation.Transaction.TYPE;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(name = "car-transfer", info = @Info(title = "Car Transfer Composite", version = "0.2.0"))
@Default
public final class CarTransfer implements ContractInterface {

    private static final String MAINT_PREFIX = "MAINT_";

    @Transaction(name = "InitLedger")
    public void initLedger(Context ctx) {
        createCar(ctx, "car1", "Toyota", "Corolla", "Blue", "Tomoko", "AutoShop");
    }

    @Transaction(name = "CreateCar")
    public String createCar(Context ctx, String id, String brand, String model, String color, String owner,
            String maintainedBy) {
        if (carExists(ctx, id)) {
            throw new ChaincodeException("Car already exists: " + id);
        }

        // 1. Create Main Entity
        CarEntityMain main = CarEntityMain.builder()
                .ID(id).Brand(brand).Model(model).Color(color).Owner(owner).build();

        // 2. Create Maintainer Entity
        CarEntityMaintainer maint = CarEntityMaintainer.builder()
                .ID(id).MaintainedBy(maintainedBy).build();

        // 3. Persist as separate keys (Logically one, physically two)
        ctx.getStub().putStringState(id, serialize(main));
        ctx.getStub().putStringState(MAINT_PREFIX + id, serialize(maint));

        return serialize(assemble(main, maint));
    }

    @Transaction(name = "ReadCar", intent = TYPE.EVALUATE)
    public String readCar(Context ctx, String id) {
        CarEntityMain main = deserialize(ctx.getStub().getStringState(id), CarEntityMain.class);
        CarEntityMaintainer maint = deserialize(ctx.getStub().getStringState(MAINT_PREFIX + id),
                CarEntityMaintainer.class);

        return serialize(assemble(main, maint));
    }

    @Transaction(name = "GetAllCars", intent = TYPE.EVALUATE)
    public String getAllCars(Context ctx) {
        List<CarBusinessObject> results = new ArrayList<>();

        // Get all main entities
        QueryResultsIterator<KeyValue> mainStates = ctx.getStub().getStateByRange("", "");

        for (KeyValue kv : mainStates) {
            String key = kv.getKey();
            // Skip maintenance keys if they appear in range
            if (key.startsWith(MAINT_PREFIX))
                continue;

            try {
                CarEntityMain main = deserialize(kv.getStringValue(), CarEntityMain.class);
                // Deep fetch the related component
                String maintJson = ctx.getStub().getStringState(MAINT_PREFIX + key);
                CarEntityMaintainer maint = (maintJson != null)
                        ? deserialize(maintJson, CarEntityMaintainer.class)
                        : CarEntityMaintainer.builder().ID(key).MaintainedBy("None").build();

                results.add(assemble(main, maint));
            } catch (Exception e) {
                // Ignore non-car data
            }
        }
        return serialize(results);
    }

    @Transaction(name = "TransferCar")
    public void transferCar(Context ctx, String id, String newOwner) {
        String mainJson = ctx.getStub().getStringState(id);
        if (mainJson == null)
            throw new ChaincodeException("Car not found");

        CarEntityMain main = deserialize(mainJson, CarEntityMain.class);
        CarEntityMain updated = main.toBuilder().Owner(newOwner).build();

        // This only touches the Main key, maintenance remains untouched (no MVCC
        // conflict there)
        ctx.getStub().putStringState(id, serialize(updated));
    }

    /**
     * Helper to assemble the components into the Business Object
     */
    private CarBusinessObject assemble(CarEntityMain main, CarEntityMaintainer maint) {
        return CarBusinessObject.builder()
                .ID(main.getID())
                .Brand(main.getBrand())
                .Model(main.getModel())
                .Color(main.getColor())
                .Owner(main.getOwner())
                .MaintainedBy(maint.getMaintainedBy())
                .build();
    }

    @Transaction(name = "CarExists", intent = TYPE.EVALUATE)
    public boolean carExists(Context ctx, String id) {
        String res = ctx.getStub().getStringState(id);
        return res != null && !res.isEmpty();
    }
}