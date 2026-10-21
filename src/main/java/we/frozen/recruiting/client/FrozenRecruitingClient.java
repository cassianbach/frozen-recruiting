package we.frozen.recruiting.client;

import net.fabricmc.api.ClientModInitializer;
import we.frozen.recruiting.FrozenRecruiting;

public class FrozenRecruitingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RecruitingInteraction.register();
		FrozenRecruiting.LOGGER.info("Frozen Recruiting ready - press G to open the recruiting menu");
	}
}