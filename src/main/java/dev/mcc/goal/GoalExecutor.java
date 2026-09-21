package dev.mcc.goal;

import dev.mcc.MccMod;
import dev.mcc.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Deterministic goal queue. Pure Java + the GoalTask interface, so the FSM logic
 * is unit-testable with fake tasks. The Brain replaces/submits plans here; the
 * SurvivalReflex preempts urgent tasks. Completion events go to a listener
 * (the Brain subscribes to trigger re-planning).
 */
public final class GoalExecutor {
	private final CompanionEntity entity;
	private final List<GoalSpec> queue = new ArrayList<>();
	private GoalTask current;
	private GoalResult lastResult;
	private Consumer<GoalResult> listener;
	private GoalTask resumedAfterUrgent;

	public GoalExecutor(CompanionEntity entity) {
		this.entity = entity;
	}

	public void push(GoalSpec spec) {
		queue.add(spec);
	}

	public void replaceQueue(List<GoalSpec> plan) {
		stopCurrent();
		queue.clear();
		queue.addAll(plan);
		current = null;
	}

	public void clear() {
		stopCurrent();
		queue.clear();
		current = null;
	}

	public Optional<GoalTask> current() { return Optional.ofNullable(current); }
	public List<GoalSpec> queue() { return List.copyOf(queue); }
	public Optional<GoalResult> lastResult() { return Optional.ofNullable(lastResult); }

	public void setListener(Consumer<GoalResult> listener) {
		this.listener = listener;
	}

	/**
	 * SurvivalReflex entry: stop the current task, run the urgent one to
	 * completion (or failure), then resume the interrupted task.
	 */
	public void preempt(GoalTask urgent) {
		GoalTask interrupted = current;
		if (interrupted != null) interrupted.stop(entity);
		resumedAfterUrgent = interrupted;
		current = urgent;
		current.start(entity);
	}

	public void tick() {
		if (current == null) {
			if (!queue.isEmpty()) {
				GoalSpec next = queue.remove(0);
				current = GoalFactory.create(next, entity);
				current.start(entity);
			} else if (resumedAfterUrgent != null) {
				current = resumedAfterUrgent;
				resumedAfterUrgent = null;
				current.start(entity);
			} else {
				return;
			}
		}

		TaskStatus status = current.tick(entity);
		if (status == TaskStatus.RUNNING) {
			return;
		}
		String reason = (status == TaskStatus.DONE) ? "done" : current.progress();
		current.stop(entity);
		lastResult = new GoalResult(current.spec(), status, reason);
		MccMod.LOGGER.info("[MCC][goal] {} {} reason={}", current.spec().type(), status, reason);
		current = null;
		if (listener != null) {
			listener.accept(lastResult);
		}
	}

	private void stopCurrent() {
		if (current != null) {
			current.stop(entity);
			current = null;
		}
	}
}