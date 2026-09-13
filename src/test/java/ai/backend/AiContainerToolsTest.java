package ai.backend;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiContainerToolsTest {
    @Test
    void validatesDiscoveryAndPositionBeforeAccessingClient() {
        validate("getnearbycontainer", "{\"range\":3}");
        validate("getnearbycontainer", "{\"range\":20}");
        for (String range : new String[]{"2", "21", "3.5", "null", "\"5\"", "1e100"}) {
            reject("getnearbycontainer", "{\"range\":" + range + "}");
        }
        validate("opencontainer", "{\"pos\":\"~ ~-1 ~2\"}");
        validate("opencontainer", "{\"pos\":\"100 64 -20\"}");
        for (String pos : new String[]{"", "1 2", "^ ^ ^", "~NaN 2 3"}) {
            reject("opencontainer", "{\"pos\":\"" + pos + "\"}");
        }
        assertTrue(new MinecraftCommandToolExecutor().execute(new AiToolCall(
                "invalid", "getnearbycontainer", "{\"range\":21}")).join().startsWith("Tool rejected"));
    }

    @Test
    void acceptsOnlySupportedClickAndButtonCombinations() {
        validate("clickcontainerslot", click("PICKUP", 0));
        validate("clickcontainerslot", click("PICKUP", 1));
        validate("clickcontainerslot", click("QUICK_MOVE", 0));
        validate("clickcontainerslot", click("SWAP", 8));
        validate("clickcontainerslot", click("SWAP", 40));
        reject("clickcontainerslot", click("PICKUP", 2));
        reject("clickcontainerslot", click("QUICK_MOVE", 1));
        reject("clickcontainerslot", click("SWAP", 9));
        reject("clickcontainerslot", click("SWAP", 39));
        for (String action : new String[]{"THROW", "CLONE", "QUICK_CRAFT", "PICKUP_ALL", "pickup"}) {
            reject("clickcontainerslot", click(action, 0));
        }
        reject("clickcontainerslot", click("PICKUP", 0).replace("\"slot\":0", "\"slot\":-999"));
        reject("clickcontainerslot", click("PICKUP", 0).replace("\"slot\":0", "\"slot\":0.5"));
        reject("clickcontainerslot", click("PICKUP", 0).replace("\"slot\":0", "\"slot\":2147483648"));
    }

    @Test
    void rejectsMissingUnexpectedAndWronglyTypedFields() {
        validate("getcontainer", "{}");
        validate("closecontainer", "{\"snapshotId\":\"snapshot\"}");
        reject("getcontainer", "{\"pos\":\"1 2 3\"}");
        reject("closecontainer", "{}");
        reject("closecontainer", "{\"snapshotId\":null}");
        reject("closecontainer", "{\"snapshotId\":1}");
        reject("closecontainer", "{\"snapshotId\":\" \"}");
        reject("clickcontainerslot", "{\"slot\":0,\"action\":\"PICKUP\",\"button\":0}");
        reject("clickcontainerslot", click("PICKUP", 0).replace("\"button\":0", "\"button\":\"0\""));
        reject("getnearbycontainer", "{\"range\":5,\"extra\":true}");
    }

    private static String click(String action, int button) {
        return "{\"snapshotId\":\"snapshot\",\"slot\":0,\"action\":\"" + action + "\",\"button\":" + button + "}";
    }

    private static void validate(String tool, String json) {
        AiContainerTools.validateArguments(tool, JsonParser.parseString(json).getAsJsonObject());
    }

    private static void reject(String tool, String json) {
        assertThrows(IllegalArgumentException.class, () -> validate(tool, json), tool + " " + json);
    }

}
