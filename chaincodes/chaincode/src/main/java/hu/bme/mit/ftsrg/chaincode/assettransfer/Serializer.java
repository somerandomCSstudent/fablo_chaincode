/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.chaincode.assettransfer;

import com.google.gson.Gson;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Serializer {

  private final Gson GSON = new Gson();

  // Changed from default to public
  public String serialize(Object obj) {
    return GSON.toJson(obj);
  }

  // Changed from default to public
  public <T> T deserialize(String data, Class<T> clazz) {
    return GSON.fromJson(data, clazz);
  }
}