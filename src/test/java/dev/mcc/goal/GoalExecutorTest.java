package dev.mcc.goal;

import dev.mcc.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4: executor FSM with fake tasks — queue order, completion listener,
 * failure propagation, preempt/resume. GoalExecutor is plain Java (a null entity
 * is fine since fake tasks never touch it), so this runs cold.
 *
 * GoalFactory is bypassed: we push GoalSpecs and stub the factory to avoid any
 * game dependency in the test. (A tiny subclass overrides the factory hook.)
 */
class GoalExecutorTest {

	/** Minimal fake task. */
	static final class FakeTask implements GoalTask {
		private final GoalSpec spec;
		private final int ticksToFinish;
		private final TaskStatus result;
		private int ticks;
		int stopCalls;

		FakeTask(GoalSpec spec, int ticksToFinish, TaskStatus result) {
			this.spec = spec; this.ticksToFinish = ticksToFinish; this.result = result;
		}
		@Override public void start(CompanionEntity e) {}
		@Override public TaskStatus tick(CompanionEntity e) {
			ticks++;
			return ticks >= ticksToFinish ? result : TaskStatus.RUNNING;
		}
		@Override public void stop(CompanionEntity e) { stopCalls++; }
		@Override public String progress() { return "fake"; }
		@Override public GoalSpec spec() { return spec; }
	}

	/** Executor with a test-injected factory so we control task construction. */
	static final class TestExecutor extends GoalExecutor {
		private final java.util.Map<GoalSpec, FakeTask> tasks = new java.util.HashMap<>();
		TestExecutor() {
			super(null);
		}
		TestExecutor addReplacement(GoalSpec s, FakeTask t) { tasks.put(s, t); return this; }

		@Override
		protected GoalTask createGoal(GoalSpec s) {
			FakeTask t = tasks.get(s);
			return t != null ? t : new FakeTask(s, 1, TaskStatus.DONE);
		}
	}

	@Test void completionFiresListener() {
		TestExecutor ex = new TestExecutor();
		List<GoalResult> results = new ArrayList<>();
		ex.setListener(results::add);
		GoalSpec s = GoalSpec.of(GoalType.WAIT);
		ex.addReplacement(s, new FakeTask(s, 2, TaskStatus.DONE));
		ex.push(s);

		ex.tick(); // start
		ex.tick(); // finish
		assertEquals(1, results.size(), "listener fired once");
		assertEquals(GoalType.WAIT, results.get(0).spec().type());
		assertEquals(TaskStatus.DONE, results.get(0).status());
	}

	@Test void failurePropagatesToListener() {
		TestExecutor ex = new TestExecutor();
		List<GoalResult> results = new ArrayList<>();
		ex.setListener(results::add);
		GoalSpec s = GoalSpec.of(GoalType.DECLINE, "nope");
		ex.addReplacement(s, new FakeTask(s, 1, TaskStatus.FAILED));
		ex.push(s);

		ex.tick(); ex.tick();
		assertEquals(1, results.size());
		assertEquals(TaskStatus.FAILED, results.get(0).status());
	}

	@Test void preemptStopsCurrentThenResumes() {
		TestExecutor ex = new TestExecutor();
		List<GoalResult> results = new ArrayList<>();
		ex.setListener(results::add);

		GoalSpec normal = GoalSpec.of(GoalType.WAIT);
		FakeTask normalTask = new FakeTask(normal, 5, TaskStatus.DONE);
		ex.addReplacement(normal, normalTask);
		ex.push(normal);
		ex.tick(); // normal started (ticks=1)

		// urgent interrupts
		GoalSpec urgent = GoalSpec.of(GoalType.GUARD);
		FakeTask urgentTask = new FakeTask(urgent, 2, TaskStatus.DONE);
		ex.preempt(urgentTask);
		ex.tick(); // urgent t1
		ex.tick(); // urgent t2 -> DONE
		assertEquals(1, normalTask.stopCalls, "normal task was stopped on preempt");

		// after urgent finishes, normal resumes and must run to its full 5 ticks
		for (int i = 0; i < 6; i++) ex.tick();
		assertEquals(5, normalTask.ticks, "normal task ran its remaining ticks");
		assertTrue(results.stream().anyMatch(r -> r.status() == TaskStatus.DONE),
			"normal completion reached the listener");
	}

	@Test void clearStopsCurrentAndDropsQueue() {
		TestExecutor ex = new TestExecutor();
		ex.push(GoalSpec.of(GoalType.WAIT));
		ex.tick();
		ex.push(GoalSpec.of(GoalType.WAIT));
		ex.push(GoalSpec.of(GoalType.WAIT));
		ex.clear();
		assertTrue(ex.current().isEmpty());
		assertTrue(ex.queue().isEmpty());
	}
}