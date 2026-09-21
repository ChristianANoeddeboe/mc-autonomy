package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;
import dev.mcc.goal.GoalExecutor;
import dev.mcc.llm.CompanionBrain;
import dev.mcc.nav.Navigator;
import dev.mcc.owner.Owner;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The companion mob. Extends PathfinderMob so we inherit GroundPathNavigation,
 * GoalSelector, FloatGoal (drowning) for free. Navigation is NOT driven by
 * registerGoals here — the deterministic GoalExecutor owns movement via
 * Navigator. This class exposes the sub-systems and the behaviour mode.
 */
public final class CompanionEntity extends PathfinderMob {
	private final CompanionInventory inventory;
	private final HungerTracker hunger;
	private final Navigator navigator;
	private final GoalExecutor executor;
	private final CompanionBrain brain;

	private Owner owner;
	private BehaviourMode mode = BehaviourMode.FOLLOW;
	private net.minecraft.core.BlockPos anchor;
	private long lastHurtTick = Integer.MIN_VALUE;

	public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.inventory = new CompanionInventory(CompanionConfig.get().items.inventorySize);
		this.hunger = new HungerTracker();
		this.navigator = new Navigator(this);
		this.executor = new GoalExecutor(this);
		this.brain = new CompanionBrain(this);
		if (level instanceof ServerLevel) {
			this.registerGoals();
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		CompanionConfig.EntityGroup cfg = CompanionConfig.get().entity;
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, cfg.maxHealth)
			.add(Attributes.MOVEMENT_SPEED, cfg.moveSpeed)
			.add(Attributes.FOLLOW_RANGE, cfg.followRange)
			.add(Attributes.ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		GroundPathNavigation nav = new GroundPathNavigation(this, level);
		nav.setCanOpenDoors(true);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	public int getMaxFallDistance() {
		return (int) CompanionConfig.get().entity.maxFallDistance;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isAlwaysTicking() {
		// Keep ticking in a headless server with no players — otherwise the
		// world pauses and the companion's tick() never runs (headless gate).
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		// Deterministic logic (hunger/goals/hunger tick) is driven from
		// ServerHooks via ServerTickEvents.END_SERVER_TICK, NOT here — that
		// hook fires even in a headless empty world, and avoids double-ticking
		// when a real player is present.
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		this.lastHurtTick = this.tickCount;
		return super.hurtServer(level, source, amount);
	}

	// ---- public component accessors ----------------------------------------

	public CompanionInventory inventory() { return inventory; }
	public HungerTracker hunger() { return hunger; }
	public Navigator navigator() { return navigator; }
	public GoalExecutor executor() { return executor; }
	public CompanionBrain brain() { return brain; }
	public BehaviourMode mode() { return mode; }
	public void setMode(BehaviourMode m) { this.mode = m; }
	public Optional<Owner> owner() { return Optional.ofNullable(owner); }
	public void setOwner(Owner o) { this.owner = o; }
	public Optional<net.minecraft.core.BlockPos> anchor() { return Optional.ofNullable(anchor); }
	public void setAnchor(net.minecraft.core.BlockPos p) { this.anchor = p; }
	public long lastHurtTick() { return lastHurtTick; }

	public void say(String text) {
		owner().ifPresent(o -> o.sendMessage(CompanionConfig.get().chat.name + ": " + text));
		dev.mcc.MccMod.LOGGER.info("[MCC][say] {}", text);
	}

	public boolean hasWeapon() {
		return !this.getMainHandItem().isEmpty()
			&& this.getMainHandItem().has(DataComponents.WEAPON);
	}

	public int weaponAttackDamage() {
		return (int) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}
}