package we.frozen.recruiting.client;

import net.fabricmc.api.ClientModInitializer;
import we.frozen.recruiting.FrozenRecruiting;
import we.frozen.recruiting.cosmetic.CosmeticCommands;
import we.frozen.recruiting.cosmetic.CosmeticService;
import we.frozen.recruiting.cosmetic.OverheadBadgeRenderer;

public class FrozenRecruitingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SentLog.load();

		CosmeticService cosmeticService = CosmeticService.get();
		cosmeticService.storage().load();
		cosmeticService.scanCodesFile();

		RecruitingInteraction.register();
		ReplyWatcher.register();
		ReplyPopup.register();
		RecruitCommands.register();
		CosmeticCommands.register();

		OverheadBadgeRenderer.register(cosmeticService.provider());

		FrozenRecruiting.LOGGER.info("Frozen Recruiting ready - press G to open the recruiting menu");
	}
}