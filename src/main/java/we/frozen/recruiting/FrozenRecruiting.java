package we.frozen.recruiting;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrozenRecruiting implements ClientModInitializer {
	public static final String MOD_ID = "frozen-recruiting";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[Frozen Recruiting] loaded");
	}
}