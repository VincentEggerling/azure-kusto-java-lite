// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.azure.kusto.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * In this lite version:
 * - We have removed all methods except generateCommandPayload()
 * - We slightly modified generateCommandPayload():
 *   - Made is public static.
 *   - Changed the exception handling to not depend on custom exceptions defined in exceptions/
 *   - Added the command.trim() that comes from the deleted method executeToJsonResult().
 */
public class ClientImpl {

    public static String generateCommandPayload(String database, String command, ClientRequestProperties properties) throws JSONException {
        String jsonPayload;

        command = command.trim();

        JSONObject json = new JSONObject()
                .put("db", database)
                .put("csl", command);

        if (properties != null) {
            json.put("properties", properties.toString());
        }

        jsonPayload = json.toString();

        return jsonPayload;
    }

}