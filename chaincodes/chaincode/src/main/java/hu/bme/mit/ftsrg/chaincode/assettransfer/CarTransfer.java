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
import org.tinylog.Logger;

@Contract(name = "car-transfer", info = @Info(title = "Car Transfer with Persistence Layer", version = "0.3.0"))
@Default
public final class CarTransfer implements ContractInterface {

    private static final String MAINT_PREFIX = "MAINT_";

    @Transaction(name = "InitLedger")
    public void initLedger(Context ctx) {
        List<CarBusinessObject> cars = List.of(
                CarBusinessObject.builder().ID("car1").Brand("Toyota").Model("Corolla").Color("Blue").Owner("Tomoko")
                        .MaintainedBy("Joseph").build(),
                CarBusinessObject.builder().ID("car2").Brand("Hyundai").Model("Kona").Color("White").Owner("Pierre")
                        .MaintainedBy("John").build(),
                CarBusinessObject.builder().ID("car3").Brand("Volswagen").Model("Golf").Color("Brown").Owner("Andreas")
                        .MaintainedBy("Gunther").build(),
                CarBusinessObject.builder().ID("car4").Brand("Kia").Model("Golf").Color("Green").Owner("John")
                        .MaintainedBy("Pierre").build());

        for (CarBusinessObject car : cars) {
            this.putCar(ctx, car);
            Logger.info("Car {} initialized", car.getID());
        }
    }

    @Transaction(name = "CreateCar")
    public String createCar(Context ctx, String id, String brand, String model, String color, String owner,
            String maintainedBy) {
        if (carExists(ctx, id)) {
            throw new ChaincodeException(String.format("The car %s already exists", id));
        }

        CarBusinessObject car = CarBusinessObject.builder()
                .ID(id).Brand(brand).Model(model).Color(color).Owner(owner).MaintainedBy(maintainedBy).build();

        this.putCar(ctx, car);
        return serialize(car);
    }

    @Transaction(name = "ReadCar", intent = TYPE.EVALUATE)
    public String readCar(Context ctx, String id) {
        if (!carExists(ctx, id)) {
            throw new ChaincodeException(String.format("The car %s does not exist", id));
        }
        return serialize(this.getCar(ctx, id));
    }

    @Transaction(name = "TransferCar")
    public String transferCar(Context ctx, String id, String newOwner) {
        CarBusinessObject car = this.getCar(ctx, id); // Hidden reassembly
        String oldOwner = car.getOwner();

        car = car.toBuilder().Owner(newOwner).build();

        this.putCar(ctx, car); // Hidden fragmentation
        return oldOwner;
    }

    @Transaction(name = "GetAllCars", intent = TYPE.EVALUATE)
    public String getAllCars(Context ctx) {
        List<CarBusinessObject> results = new ArrayList<>();
        QueryResultsIterator<KeyValue> states = ctx.getStub().getStateByRange("", "");

        for (KeyValue kv : states) {
            String key = kv.getKey();
            if (key.startsWith(MAINT_PREFIX))
                continue;

            try {
                results.add(this.getCar(ctx, key));
            } catch (Exception e) {
                Logger.error("Error reassembling car {}: {}", key, e.getMessage());
            }
        }
        return serialize(results);
    }

    // Persistence Logic: Fragmentation & Reassembly
    /**
     * Splits the Business Object into Entity components and writes to ledger
     */
    private void putCar(Context ctx, CarBusinessObject car) {
        // Shard into Main Entity
        CarEntityMain main = CarEntityMain.builder()
                .ID(car.getID()).Brand(car.getBrand()).Model(car.getModel())
                .Color(car.getColor()).Owner(car.getOwner()).build();

        // Shard into Maintainer Entity
        CarEntityMaintainer maint = CarEntityMaintainer.builder()
                .ID(car.getID()).MaintainedBy(car.getMaintainedBy()).build();

        // Physical Persistence
        ctx.getStub().putStringState(main.getID(), serialize(main));
        ctx.getStub().putStringState(MAINT_PREFIX + maint.getID(), serialize(maint));
    }

    /**
     * Reads fragmented components from ledger and hydrates the Business Object
     */
    private CarBusinessObject getCar(Context ctx, String id) {
        String mainJson = ctx.getStub().getStringState(id);
        String maintJson = ctx.getStub().getStringState(MAINT_PREFIX + id);

        if (mainJson == null || mainJson.isEmpty()) {
            throw new ChaincodeException("Car Entity Main not found for ID: " + id);
        }

        CarEntityMain main = deserialize(mainJson, CarEntityMain.class);
        CarEntityMaintainer maint = (maintJson != null && !maintJson.isEmpty())
                ? deserialize(maintJson, CarEntityMaintainer.class)
                : CarEntityMaintainer.builder().ID(id).MaintainedBy("Unknown").build();

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