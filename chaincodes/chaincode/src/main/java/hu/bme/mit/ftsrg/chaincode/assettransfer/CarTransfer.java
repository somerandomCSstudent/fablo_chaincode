/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.chaincode.assettransfer;

import static hu.bme.mit.ftsrg.chaincode.assettransfer.Serializer.*;

import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.contract.annotation.Transaction.TYPE;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.tinylog.Logger;

@Contract(name = "car-transfer", info = @Info(title = "Car Transfer", description = "Chaincode for managing and transferring vehicle ownership", version = "0.1.0", license = @License(name = "Apache-2.0"), contact = @Contact(email = "support@car-registry.com", name = "Car Registry Admin", url = "http://car-registry.com")))
@Default
public final class CarTransfer implements ContractInterface {

    @Transaction(name = "Ping")
    public String ping(Context ignoredCtx) {
        return "pong";
    }

    @Transaction(name = "InitLedger")
    public void initLedger(Context ctx) {
        // Initializing the ledger with sample Car data
        List<Car> cars = List.of(
                Car.builder().ID("car1").Make("Toyota").Model("Corolla").Color("Blue").Owner("Tomoko").Mileage(15000)
                        .build(),
                Car.builder().ID("car2").Make("Ford").Model("Mustang").Color("Red").Owner("Brad").Mileage(5000).build(),
                Car.builder().ID("car3").Make("Tesla").Model("Model 3").Color("White").Owner("Jin Soo").Mileage(2000)
                        .build());

        for (var car : cars) {
            ctx.getStub().putStringState(car.ID(), serialize(car));
            Logger.info("Car {} initialized", car.ID());
        }
    }

    @Transaction(name = "CreateCar")
    public String createCar(
            Context ctx, String id, String make, String model, String color, String owner, int mileage) {
        assertNotExists(ctx, id);

        var car = Car.builder()
                .ID(id)
                .Make(make)
                .Model(model)
                .Color(color)
                .Owner(owner)
                .Mileage(mileage)
                .build();

        ctx.getStub().putStringState(car.ID(), serialize(car));
        return serialize(car);
    }

    @Transaction(name = "ReadCar", intent = TYPE.EVALUATE)
    public String readCar(Context ctx, String id) {
        return serialize(carByID(ctx, id));
    }

    @Transaction(name = "UpdateCar")
    public String updateCar(
            Context ctx, String id, String make, String model, String color, String owner, int mileage) {
        assertExists(ctx, id);

        var updatedCar = Car.builder()
                .ID(id)
                .Make(make)
                .Model(model)
                .Color(color)
                .Owner(owner)
                .Mileage(mileage)
                .build();

        ctx.getStub().putStringState(id, serialize(updatedCar));
        return serialize(updatedCar);
    }

    @Transaction(name = "DeleteCar")
    public String deleteCar(Context ctx, String id) {
        assertExists(ctx, id);
        ctx.getStub().delState(id);
        return id;
    }

    @Transaction(name = "TransferCar")
    public String transferCar(Context ctx, String id, String newOwner) {
        assertExists(ctx, id);

        Car car = carByID(ctx, id);
        final String oldOwner = car.Owner();
        car = car.toBuilder().Owner(newOwner).build();

        ctx.getStub().putStringState(id, serialize(car));
        return oldOwner;
    }

    @Transaction(name = "GetAllCars", intent = TYPE.EVALUATE)
    public String getAllCars(Context ctx) {
        var answer = new ArrayList<Car>();

        QueryResultsIterator<KeyValue> results = ctx.getStub().getStateByRange("", "");
        for (KeyValue result : results) {
            Car car = deserialize(result.getStringValue(), Car.class);
            answer.add(car);
        }

        return serialize(answer);
    }

    @Transaction(name = "CarExists", intent = TYPE.EVALUATE)
    public boolean carExists(Context ctx, String id) {
        String carJson = ctx.getStub().getStringState(id);
        return carJson != null && !carJson.isEmpty();
    }

    private void assertNotExists(Context ctx, String id) {
        if (carExists(ctx, id)) {
            throw new ChaincodeException(String.format("The car %s already exists", id));
        }
    }

    private void assertExists(Context ctx, String id) {
        if (!carExists(ctx, id)) {
            throw new ChaincodeException(String.format("The car %s does not exist", id));
        }
    }

    private Car carByID(Context ctx, String id) {
        assertExists(ctx, id);
        return deserialize(ctx.getStub().getStringState(id), Car.class);
    }
}