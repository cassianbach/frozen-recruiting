package we.frozen.recruiting.client;

import net.fabricmc.api.ClientModInitializer;
import we.frozen.recruiting.FrozenRecruiting;

public class FrozenRecruitingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SentLog.load();

		RecruitingInteraction.register();
		ReplyWatcher.register();
		ReplyPopup.register();
		RecruitCommands.register();

		FrozenRecruiting.LOGGER.info("Frozen Recruiting ready - press G to open the recruiting menu");
	}
}