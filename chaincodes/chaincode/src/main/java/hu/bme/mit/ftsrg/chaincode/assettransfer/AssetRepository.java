package hu.bme.mit.ftsrg.chaincode.assettransfer;

import static hu.bme.mit.ftsrg.chaincode.assettransfer.Serializer.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.tinylog.Logger;

public class AssetRepository<T> {
    private static final String SHARD_PREFIX = "SHARD_";
    private final Class<T> clazz;

    public AssetRepository(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Shards an asset and writes it to the ledger.
     */
    public void put(Context ctx, T asset) {
        String id = getAssetId(asset);
        Sharding shardingAnnotation = asset.getClass().getAnnotation(Sharding.class);

        if (shardingAnnotation == null) {
            ctx.getStub().putStringState(id, serialize(asset));
            return;
        }

        try {
            Class<? extends ShardingDefinition> defClass = shardingAnnotation.shardingClass();
            ShardingDefinition definition = defClass.getDeclaredConstructor().newInstance();
            List<List<String>> shardConfig = definition.getShards();

            for (int i = 0; i < shardConfig.size(); i++) {
                Map<String, Object> shardMap = new HashMap<>();
                for (String fieldName : shardConfig.get(i)) {
                    shardMap.put(fieldName, getFieldValue(asset, fieldName));
                }
                ctx.getStub().putStringState(id + "_" + SHARD_PREFIX + i, serialize(shardMap));
            }
            ctx.getStub().putStringState(id, "SHARDED_MASTER");

        } catch (ReflectiveOperationException e) {
            throw new ChaincodeException("Failed to shard asset: " + id, e);
        }
    }

    /**
     * Reassembles an asset from its shards on the ledger.
     */
    public T get(Context ctx, String id) {
        String masterData = ctx.getStub().getStringState(id);
        if (masterData == null || masterData.isEmpty()) {
            throw new ChaincodeException("Asset not found: " + id);
        }

        Sharding shardingAnnotation = clazz.getAnnotation(Sharding.class);

        if (shardingAnnotation == null || !masterData.equals("SHARDED_MASTER")) {
            return deserialize(masterData, clazz);
        }

        try {
            Method builderMethod = clazz.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            setBuilderId(builder, id);

            ShardingDefinition definition = shardingAnnotation.shardingClass().getDeclaredConstructor().newInstance();
            List<List<String>> shardConfig = definition.getShards();

            for (int i = 0; i < shardConfig.size(); i++) {
                String shardJson = ctx.getStub().getStringState(id + "_" + SHARD_PREFIX + i);
                if (shardJson == null || shardJson.isEmpty())
                    continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> shardMap = deserialize(shardJson, Map.class);
                for (Map.Entry<String, Object> entry : shardMap.entrySet()) {
                    setBuilderValue(builder, entry.getKey(), entry.getValue());
                }
            }

            Method buildMethod = builder.getClass().getMethod("build");
            return clazz.cast(buildMethod.invoke(builder));

        } catch (ReflectiveOperationException e) {
            throw new ChaincodeException("Failed to reassemble asset: " + id, e);
        }
    }

    /**
     * Deletes an asset and all its associated shards.
     */
    public void delete(Context ctx, String id) {
        String masterData = ctx.getStub().getStringState(id);
        if (masterData == null || masterData.isEmpty())
            return;

        Sharding shardingAnnotation = clazz.getAnnotation(Sharding.class);
        if (shardingAnnotation != null && "SHARDED_MASTER".equals(masterData)) {
            try {
                ShardingDefinition definition = shardingAnnotation.shardingClass().getDeclaredConstructor()
                        .newInstance();
                for (int i = 0; i < definition.getShards().size(); i++) {
                    ctx.getStub().delState(id + "_" + SHARD_PREFIX + i);
                }
            } catch (ReflectiveOperationException e) {
                Logger.error("Failed to delete shards for asset: {}", id);
            }
        }
        ctx.getStub().delState(id);
    }

    /* --- REFLECTION HELPERS --- */

    private Object getFieldValue(Object asset, String fieldName) throws ReflectiveOperationException {
        String cap = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String[] candidates = { "get" + cap, fieldName, fieldName.toLowerCase() };

        for (String methodName : candidates) {
            try {
                return asset.getClass().getMethod(methodName).invoke(asset);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("Could not find getter for: " + fieldName);
    }

    private Object getFieldValueFallback(Object asset, String fieldName) throws ReflectiveOperationException {
        try {
            String cap = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            return asset.getClass().getMethod("get" + cap).invoke(asset);
        } catch (NoSuchMethodException ex) {
            return asset.getClass().getMethod(fieldName).invoke(asset);
        }
    }

    private void setBuilderValue(Object builder, String fieldName, Object value) {
        try {
            Method setter = builder.getClass().getMethod(fieldName, String.class);
            setter.invoke(builder, value.toString());
        } catch (ReflectiveOperationException e) {
            setBuilderValueFallback(builder, fieldName, value);
        }
    }

    private void setBuilderValueFallback(Object builder, String fieldName, Object value) {
        try {
            String lower = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
            Method setter = builder.getClass().getMethod(lower, String.class);
            setter.invoke(builder, value.toString());
        } catch (ReflectiveOperationException ex) {
            Logger.warn("Could not set field {} on builder", fieldName);
        }
    }

    private void setBuilderId(Object builder, String id) throws ReflectiveOperationException {
        try {
            builder.getClass().getMethod("idNum", String.class).invoke(builder, id);
        } catch (NoSuchMethodException e) {
            setBuilderIdFallback(builder, id);
        }
    }

    private void setBuilderIdFallback(Object builder, String id) throws ReflectiveOperationException {
        try {
            builder.getClass().getMethod("ID", String.class).invoke(builder, id);
        } catch (NoSuchMethodException ex) {
            builder.getClass().getMethod("id", String.class).invoke(builder, id);
        }
    }

    private String getAssetId(Object o) {
        try {
            return tryGetIdNum(o);
        } catch (ReflectiveOperationException e) {
            throw new ChaincodeException("Asset missing valid ID method", e);
        }
    }

    private String tryGetIdNum(Object o) throws ReflectiveOperationException {
        try {
            return (String) o.getClass().getMethod("getIdNum").invoke(o);
        } catch (NoSuchMethodException e) {
            try {
                return (String) o.getClass().getMethod("idNum").invoke(o);
            } catch (NoSuchMethodException e2) {
                return (String) o.getClass().getMethod("getID").invoke(o);
            }
        }
    }
}