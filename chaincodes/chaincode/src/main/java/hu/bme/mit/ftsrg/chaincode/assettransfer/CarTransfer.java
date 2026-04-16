/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.chaincode.assettransfer;

import static hu.bme.mit.ftsrg.chaincode.assettransfer.Serializer.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.contract.annotation.Transaction.TYPE;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.tinylog.Logger;

@Contract(name = "car-transfer", info = @Info(title = "Car Transfer with CarSharding and Metaprogramming", version = "0.4.0"))
@Default
public final class CarTransfer implements ContractInterface {

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
            this.putCar(ctx, car);
            Logger.info("Car {} initialized", car.getIdNum());
        }
    }

    @Transaction(name = "CreateCar")
    public String createCar(Context ctx, String id, String brand, String model, String color, String owner,
            String maintainedBy) {
        if (carExists(ctx, id)) {
            throw new ChaincodeException(String.format("The car %s already exists", id));
        }

        CarBusinessObject car = CarBusinessObject.builder()
                .idNum(id).brand(brand).model(model).color(color).owner(owner).maintainedBy(maintainedBy).build();

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
        CarBusinessObject car = this.getCar(ctx, id);
        String oldOwner = car.getOwner();

        car = car.toBuilder().owner(newOwner).build();

        this.putCar(ctx, car);
        return oldOwner;
    }

    @Transaction(name = "GetAllCars", intent = TYPE.EVALUATE)
    public String getAllCars(Context ctx) {
        List<CarBusinessObject> results = new ArrayList<>();
        QueryResultsIterator<KeyValue> states = ctx.getStub().getStateByRange("", "");

        for (KeyValue kv : states) {
            String key = kv.getKey();

            if (key.contains(SHARD_PREFIX)) {
                continue;
            }

            try {
                results.add(this.getCar(ctx, key));
            } catch (Exception e) {
                Logger.error("Error reassembling car {}: {}", key, e.getMessage());
            }
        }
        return serialize(results);
    }

    /**
     * Reflectively shards a CarBusinessObject and writes multiple keys to the
     * ledger.
     */
    private void putCar(Context ctx, CarBusinessObject car) {
        CarSharding sharding = new CarSharding();
        List<List<String>> shardConfig = sharding.getShards();
        String carId = car.getIdNum();

        for (int i = 0; i < shardConfig.size(); i++) {
            Map<String, Object> shardMap = new HashMap<>();
            List<String> fieldNames = shardConfig.get(i);

            for (String fieldName : fieldNames) {
                try {
                    Method getter = CarBusinessObject.class.getMethod("get" + fieldName);
                    shardMap.put(fieldName, getter.invoke(car));
                } catch (Exception e) {
                    throw new ChaincodeException("Failed to reflectively read: " + fieldName, e);
                }
            }
            ctx.getStub().putStringState(carId + "_" + SHARD_PREFIX + i, serialize(shardMap));
        }
        ctx.getStub().putStringState(carId, "MASTER");
    }

    /**
     * Reflectively reassembles a CarBusinessObject by reading shards from the
     * ledger.
     */
    private CarBusinessObject getCar(Context ctx, String id) {
        CarBusinessObject.CarBusinessObjectBuilder builder = CarBusinessObject.builder().idNum(id);
        CarSharding sharding = new CarSharding();
        List<List<String>> shardConfig = sharding.getShards();

        for (int i = 0; i < shardConfig.size(); i++) {
            String shardJson = ctx.getStub().getStringState(id + "_" + SHARD_PREFIX + i);
            if (shardJson == null || shardJson.isEmpty())
                continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> shardMap = deserialize(shardJson, Map.class);

            for (Map.Entry<String, Object> entry : shardMap.entrySet()) {
                try {
                    Method setter = builder.getClass().getMethod(entry.getKey(), String.class);
                    setter.invoke(builder, entry.getValue().toString());
                } catch (Exception e) {
                    Logger.error("Method reflection failed for: " + entry.getKey());
                }
            }
        }
        return builder.build();
    }

    @Transaction(name = "CarExists", intent = TYPE.EVALUATE)
    public boolean carExists(Context ctx, String id) {
        String res = ctx.getStub().getStringState(id);
        return res != null && !res.isEmpty() && !res.contains(SHARD_PREFIX);
    }
}