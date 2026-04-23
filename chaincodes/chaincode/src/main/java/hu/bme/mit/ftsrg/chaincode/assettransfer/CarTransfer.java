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

@Contract(name = "car-transfer", info = @Info(title = "Car Transfer with AssetRepository", version = "0.5.0"))
@Default
public final class CarTransfer implements ContractInterface {

    // AssetRepository for data management
    private final AssetRepository<CarBusinessObject> repo = new AssetRepository<>(CarBusinessObject.class);
    private static final String SHARD_PREFIX = "SHARD_";

    @Transaction(name = "InitLedger")
    public void initLedger(Context ctx) {
        List<CarBusinessObject> cars = List.of(
                CarBusinessObject.builder().idNum("car1").brand("Toyota").model("Corolla").color("Blue").owner("Tomoko")
                        .maintainedBy("Joseph").build(),
                CarBusinessObject.builder().idNum("car2").brand("Hyundai").model("Kona").color("White").owner("Pierre")
                        .maintainedBy("John").build(),
                CarBusinessObject.builder().idNum("car3").brand("Volswagen").model("Golf").color("Brown")
                        .owner("Andreas")
                        .maintainedBy("Gunther").build(),
                CarBusinessObject.builder().idNum("car4").brand("Kia").model("Golf").color("Green").owner("John")
                        .maintainedBy("Pierre").build());

        for (CarBusinessObject car : cars) {
            repo.put(ctx, car);
        }
    }

    @Transaction(name = "CreateCar")
    public void createCar(Context ctx, String id, String brand, String model, String color, String owner,
            String maintainer) {
        assertNotExists(ctx, id);

        CarBusinessObject car = CarBusinessObject.builder()
                .idNum(id)
                .brand(brand)
                .model(model)
                .color(color)
                .owner(owner)
                .maintainedBy(maintainer)
                .build();

        repo.put(ctx, car);
    }

    @Transaction(name = "ReadCar", intent = TYPE.EVALUATE)
    public String readCar(Context ctx, String id) {
        CarBusinessObject car = repo.get(ctx, id);
        return serialize(car);
    }

    @Transaction(name = "UpdateCar")
    public void updateCar(Context ctx, String id, String brand, String model, String color, String owner,
            String maintainer) {
        assertExists(ctx, id);

        CarBusinessObject car = CarBusinessObject.builder()
                .idNum(id)
                .brand(brand)
                .model(model)
                .color(color)
                .owner(owner)
                .maintainedBy(maintainer)
                .build();

        repo.put(ctx, car);
    }

    @Transaction(name = "TransferCar")
    public void transferCar(Context ctx, String id, String newOwner) {
        CarBusinessObject car = repo.get(ctx, id);
        CarBusinessObject updated = car.toBuilder().owner(newOwner).build();
        repo.put(ctx, updated);
    }

    @Transaction(name = "DeleteCar")
    public void deleteCar(Context ctx, String id) {
        assertExists(ctx, id);
        repo.delete(ctx, id);
    }

    @Transaction(name = "GetAllCars", intent = TYPE.EVALUATE)
    public String getAllCars(Context ctx) {
        var results = new ArrayList<CarBusinessObject>();
        QueryResultsIterator<KeyValue> states = ctx.getStub().getStateByRange("", "");

        for (KeyValue state : states) {
            String key = state.getKey();
            if (key.contains(SHARD_PREFIX))
                continue;

            try {
                results.add(repo.get(ctx, key));
            } catch (Exception e) {
                // Ignore keys that are not CarBusinessObject master records
            }
        }
        return serialize(results);
    }

    @Transaction(name = "CarExists", intent = TYPE.EVALUATE)
    public boolean carExists(Context ctx, String id) {
        String res = ctx.getStub().getStringState(id);
        return res != null && !res.isEmpty();
    }

    private void assertNotExists(Context ctx, String id) {
        if (carExists(ctx, id)) {
            throw new ChaincodeException("The car " + id + " already exists");
        }
    }

    private void assertExists(Context ctx, String id) {
        if (!carExists(ctx, id)) {
            throw new ChaincodeException("The car " + id + " does not exist");
        }
    }
}