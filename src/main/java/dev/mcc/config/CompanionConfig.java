package dev.mcc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single Gson POJO holding every tunable. Loaded once at mod init from
 * <configDir>/mc-companion/config.json; defaults written if absent. Every field
 * is clamped to its documented bounds on load so a hand-edited file cannot break
 * the runtime. All defaults are headless-fast.
 */
public final class CompanionConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("MCC");
	private static CompanionConfig INSTANCE;

	public EntityGroup entity = new EntityGroup();
	public HungerGroup hunger = new HungerGroup();
	public NavGroup nav = new NavGroup();
	public MineGroup mine = new MineGroup();
	public ItemsGroup items = new ItemsGroup();
	public CombatGroup combat = new CombatGroup();
	public SleepGroup sleep = new SleepGroup();
	public LlmGroup llm = new LlmGroup();
	public ChatGroup chat = new ChatGroup();

	public static CompanionConfig get() {
		if (INSTANCE == null) {
			INSTANCE = new CompanionConfig();
		}
		return INSTANCE;
	}

	/** Reads config.json, writes defaults if absent, then clamps. */
	public static void load() {
		INSTANCE = new CompanionConfig();
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("mc-companion");
		Path file = dir.resolve("config.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		if (!Files.exists(file)) {
			try {
				Files.createDirectories(dir);
				Files.writeString(file, gson.toJson(INSTANCE));
				LOGGER.info("[MCC][config] wrote defaults to {}", file);
			} catch (IOException e) {
				LOGGER.warn("[MCC][config] could not write defaults: {}", e.toString());
			}
		} else {
			try {
				INSTANCE = gson.fromJson(Files.readString(file), CompanionConfig.class);
			} catch (Exception e) {
				LOGGER.warn("[MCC][config] could not parse {}, using defaults: {}", file, e.toString());
				INSTANCE = new CompanionConfig();
			}
		}
		if (INSTANCE == null) {
			INSTANCE = new CompanionConfig();
		}
		INSTANCE.clamp();
		LOGGER.info("[MCC][config] loaded from {}", file);
	}

	public void clamp() {
		entity.clamp();
		hunger.clamp();
		nav.clamp();
		mine.clamp();
		items.clamp();
		combat.clamp();
		sleep.clamp();
		llm.clamp();
		chat.clamp();
	}

	// ---- nested groups ------------------------------------------------------

	public static class EntityGroup {
		public float maxHealth = 20;
		public float moveSpeed = 0.3f;
		public float followRange = 48;
		public float maxFallDistance = 3;
		public boolean onePerPlayer = true;

		public void clamp() {
			maxHealth = Math.max(1, Math.min(100, maxHealth));
			moveSpeed = Math.max(0.1f, Math.min(1f, moveSpeed));
			followRange = Math.max(16, Math.min(96, followRange));
			maxFallDistance = Math.max(1, Math.min(10, maxFallDistance));
		}
	}

	public static class HungerGroup {
		public int max = 20;
		public int decayIntervalTicks = 400;
		public int eatBelow = 12;
		public int starveDamageIntervalTicks = 80;
		public double starveDamage = 1.0;
		public int healAboveHunger = 17;
		public int healIntervalTicks = 80;

		public void clamp() {
			max = Math.max(1, Math.min(2000, max));
			decayIntervalTicks = Math.max(1, decayIntervalTicks);
			eatBelow = Math.max(0, Math.min(max, eatBelow));
			starveDamageIntervalTicks = Math.max(1, starveDamageIntervalTicks);
			starveDamage = Math.max(0, Math.min(20, starveDamage));
			healAboveHunger = Math.max(0, Math.min(max, healAboveHunger));
			healIntervalTicks = Math.max(1, healIntervalTicks);
		}
	}

	public static class NavGroup {
		public double reachDist = 1.5;
		public int stuckTicks = 40;
		public int maxRepaths = 3;
		public int goalTimeoutTicks = 1200;
		public boolean allowTeleport = true;
		public double teleportMaxDist = 24;
		public double followMinDist = 3;
		public double followMaxDist = 10;
		public double leashDist = 32;

		public void clamp() {
			reachDist = Math.max(0.5, Math.min(8, reachDist));
			stuckTicks = Math.max(1, stuckTicks);
			maxRepaths = Math.max(0, maxRepaths);
			goalTimeoutTicks = Math.max(20, goalTimeoutTicks);
			teleportMaxDist = Math.max(1, Math.min(200, teleportMaxDist));
			followMinDist = Math.max(0, followMinDist);
			followMaxDist = Math.max(followMinDist, followMaxDist);
			leashDist = Math.max(followMaxDist, leashDist);
		}
	}

	public static class MineGroup {
		public int scanRadius = 12;
		public int scanRadiusY = 6;
		public double reachDist = 4.5;
		public double speedMultiplier = 1.0;
		public int maxBreakTicks = 100;
		public int minBreakTicks = 5;
		public int maxTargetsPerGoal = 64;

		public void clamp() {
			scanRadius = Math.max(1, Math.min(64, scanRadius));
			scanRadiusY = Math.max(1, Math.min(32, scanRadiusY));
			reachDist = Math.max(1, Math.min(16, reachDist));
			speedMultiplier = Math.max(0.1, Math.min(5, speedMultiplier));
			maxBreakTicks = Math.max(minBreakTicks, maxBreakTicks);
			minBreakTicks = Math.max(1, minBreakTicks);
			maxTargetsPerGoal = Math.max(1, maxTargetsPerGoal);
		}
	}

	public static class ItemsGroup {
		public double pickupRadius = 3.0;
		public int pickupIntervalTicks = 10;
		public double dropDistance = 1.0;
		public int inventorySize = 36;
		public double chestRadius = 8;

		public void clamp() {
			pickupRadius = Math.max(0.5, Math.min(16, pickupRadius));
			pickupIntervalTicks = Math.max(1, pickupIntervalTicks);
			dropDistance = Math.max(0.5, Math.min(16, dropDistance));
			inventorySize = Math.max(1, Math.min(128, inventorySize));
			chestRadius = Math.max(1, Math.min(32, chestRadius));
		}
	}

	public static class CombatGroup {
		public double fleeRadius = 8;
		public double fleeHealthBelow = 10;
		public double fightHealthAbove = 14;
		public int dangerScanIntervalTicks = 10;
		public double fleeDistance = 12;
		public double threatRadius = 12.0;
		public double minHealthFractionToFight = 0.5;
		public double stanceFightHealthFraction = 0.3;
		public double disengageHealthFraction = 0.25;
		public int maxHostilesToFight = 1;
		public int armorBonusThreshold = 8;
		public boolean requireWeaponDefaultMode = true;
		public int attackerMemoryTicks = 200;
		public double followProtectRadius = 6.0;
		public double approachSpeed = 1.2;
		public double backoffSpeed = 1.0;
		public int repathTicks = 10;
		public int attackCooldownTicks = 20;
		public int backoffTicks = 8;
		public double backoffDistance = 2.5;
		public double loseRange = 20.0;
		public double retargetRadius = 10.0;
		public int pathStuckTicks = 60;
		public int maxEngageTicks = 600;
		public String[] avoidTypes = {"minecraft:creeper", "minecraft:enderman", "minecraft:warden", "minecraft:ravager"};

		public void clamp() {
			fleeRadius = Math.max(1, Math.min(32, fleeRadius));
			fleeHealthBelow = Math.max(1, Math.min(100, fleeHealthBelow));
			fightHealthAbove = Math.max(1, Math.min(100, fightHealthAbove));
			dangerScanIntervalTicks = Math.max(1, dangerScanIntervalTicks);
			fleeDistance = Math.max(2, fleeDistance);
			threatRadius = Math.max(2, Math.min(64, threatRadius));
			minHealthFractionToFight = Math.max(0.05, Math.min(1, minHealthFractionToFight));
			stanceFightHealthFraction = Math.max(0.05, Math.min(1, stanceFightHealthFraction));
			disengageHealthFraction = Math.max(0.05, Math.min(minHealthFractionToFight, disengageHealthFraction));
			maxHostilesToFight = Math.max(1, maxHostilesToFight);
			armorBonusThreshold = Math.max(0, armorBonusThreshold);
			attackerMemoryTicks = Math.max(1, attackerMemoryTicks);
			followProtectRadius = Math.max(1, followProtectRadius);
			approachSpeed = Math.max(0.2, Math.min(4, approachSpeed));
			backoffSpeed = Math.max(0.2, Math.min(4, backoffSpeed));
			repathTicks = Math.max(1, repathTicks);
			attackCooldownTicks = Math.max(1, attackCooldownTicks);
			backoffTicks = Math.max(0, backoffTicks);
			backoffDistance = Math.max(0.5, backoffDistance);
			loseRange = Math.max(2, loseRange);
			retargetRadius = Math.max(1, retargetRadius);
			pathStuckTicks = Math.max(1, pathStuckTicks);
			maxEngageTicks = Math.max(1, maxEngageTicks);
		}
	}

	public static class SleepGroup {
		public boolean enabled = true;
		public double bedRadius = 16;
		public int nightStart = 13000;
		public int nightEnd = 23000;

		public void clamp() {
			bedRadius = Math.max(1, Math.min(64, bedRadius));
			nightStart = Math.max(0, Math.min(24000, nightStart));
			nightEnd = Math.max(0, Math.min(24000, nightEnd));
		}
	}

	public static class LlmGroup {
		public String mode = "http"; // http | mock | off
		public String baseUrl = "http://127.0.0.1:11434";
		public String model = "llama3.1";
		public String apiKey = "";
		public int timeoutMs = 8000;
		public int periodicTicks = 400;
		public int minIntervalTicks = 60;
		public int maxPlanLength = 4;
		public int degradedAfterFailures = 2;
		public int offlineAfterFailures = 5;
		public int probeIntervalTicks = 200;
		public int backoffBaseMs = 1000;
		public int backoffMaxMs = 30000;
		public String launchCommand = "";
		public int[] launchPorts = {11434, 11435, 11436};
		public int payloadBlockRadius = 12;
		public int payloadEntityRadius = 16;
		public int payloadMaxChars = 3000;
		public String mockScript = "mock_script.json";

		public void clamp() {
			if (mode == null || !(mode.equals("http") || mode.equals("mock") || mode.equals("off"))) {
				mode = "http";
			}
			timeoutMs = Math.max(250, Math.min(60000, timeoutMs));
			periodicTicks = Math.max(20, periodicTicks);
			minIntervalTicks = Math.max(1, Math.min(periodicTicks, minIntervalTicks));
			maxPlanLength = Math.max(1, Math.min(10, maxPlanLength));
			degradedAfterFailures = Math.max(1, degradedAfterFailures);
			offlineAfterFailures = Math.max(degradedAfterFailures, offlineAfterFailures);
			probeIntervalTicks = Math.max(20, probeIntervalTicks);
			backoffBaseMs = Math.max(100, backoffBaseMs);
			backoffMaxMs = Math.max(backoffBaseMs, backoffMaxMs);
			payloadBlockRadius = Math.max(1, Math.min(64, payloadBlockRadius));
			payloadEntityRadius = Math.max(1, Math.min(64, payloadEntityRadius));
			payloadMaxChars = Math.max(500, payloadMaxChars);
		}
	}

	public static class ChatGroup {
		public String[] prefixes = {"companion:", "comp:"};
		public String name = "Companion";
		public String logPrefix = "[MCC]";

		public void clamp() {
			if (prefixes == null || prefixes.length == 0) {
				prefixes = new String[]{"companion:", "comp:"};
			}
			if (name == null || name.isEmpty()) {
				name = "Companion";
			}
		}
	}
}