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

import org.hyperledger.fabric.shim.ChaincodeException;

public class AssetRepository {
    private static final String SHARD_PREFIX = "SHARD_";

    /**
     * Reflectively shards an Asset and writes multiple keys to the
     * ledger.
     */
    private void putAsset(Context ctx, Object o) {
        Sharding shardingAnnotation = o.getClass().getAnnotation(Sharding.class);
        if (shardingAnnotation == null) {
            // TODO, if no sharding annotation is present, we can just serialize the whole
            // object and store it under a single key. This is for backward compatibility
            // with non-sharded assets.
            /// ctx.ge
        }
        ShardingDefinition sharding = shardingAnnotation.shardingClass().getDeclaredConstructor().newInstance();
        List<List<String>> shardConfig = sharding.getShards();
        // String carId = car.getIdNum();

        for (int i = 0; i < shardConfig.size(); i++) {
            Map<String, Object> shardMap = new HashMap<>();
            List<String> fieldNames = shardConfig.get(i);

            for (String fieldName : fieldNames) {
                try {
                    Method getter = CarBusinessObject.class.getMethod("get" + fieldName);
                    shardMap.put(fieldName, getter.invoke(o));
                } catch (Exception e) {
                    throw new ChaincodeException("Failed to reflectively read: " + fieldName, e);
                }
            }
            ctx.getStub().putStringState(o.getClass().getName() + "_" + SHARD_PREFIX + i, serialize(shardMap));
        }
    }

    /**
     * Reflectively reassembles an Asset by reading shards from the
     * ledger.
     */
    private CarBusinessObject getAsset(Context ctx, String id) {
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
}
