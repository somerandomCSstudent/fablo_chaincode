package hu.bme.mit.ftsrg.chaincode.assettransfer;

import java.util.List;

public class AssetMetaProvider {
    List<Asset> assets;

    void registerAsset(Asset asset) {
        assets.add(asset);
    }

    List<String> getShardsFor(Object o) {
        String name = o.getClass().getName();
        return null;
    }
}
