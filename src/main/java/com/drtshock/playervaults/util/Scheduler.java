/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.util;

import com.drtshock.playervaults.PlayerVaults;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scheduling abstraction that works on both regular Bukkit/Spigot/Paper servers and on
 * <a href="https://github.com/PaperMC/Folia">Folia</a>.
 * <p>
 * Folia doesn't support {@link org.bukkit.scheduler.BukkitScheduler}, so this
 * reflectively dispatches to Folia's schedulers ({@code GlobalRegionScheduler},
 * {@code AsyncScheduler} and the per-entity {@code EntityScheduler}). it uses reflection
 * because those classes aren't present in Spigot. When the server isn't running folia,
 * it uses the regular Bukkit scheduler.
 * <p>
 * Delays and periods are done in ticks (for the normal Bukkit API)
 * for the async scheduler ticks are converted to milliseconds (1:50).
 */
public final class Scheduler {

    /**
     * A handle to a scheduled task. Wraps either a Bukkit {@link BukkitTask} or a Folia
     * {@code ScheduledTask} so callers can cancel repeating work normally.
     */
    public interface Task {
        void cancel();
    }

    private static final long MS_PER_TICK = 50L;

    private static final boolean FOLIA;

    private static Object globalRegionScheduler;
    private static Object asyncScheduler;
    private static Method entityGetScheduler;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncRunAtFixedRate;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method scheduledTaskCancel;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
        if (FOLIA) {
            try {
                initFolia();
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to initialise Folia scheduler bridge", t);
            }
        }
    }

    private Scheduler() {
    }

    private static void initFolia() throws Exception {
        Class<?> scheduledTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        scheduledTaskCancel = scheduledTask.getMethod("cancel");

        Class<?> globalCls = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        Class<?> asyncCls = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        Class<?> entityCls = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");

        globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
        asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
        entityGetScheduler = Entity.class.getMethod("getScheduler");

        globalRun = globalCls.getMethod("run", Plugin.class, Consumer.class);
        globalRunDelayed = globalCls.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
        globalRunAtFixedRate = globalCls.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

        asyncRunNow = asyncCls.getMethod("runNow", Plugin.class, Consumer.class);
        asyncRunDelayed = asyncCls.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
        asyncRunAtFixedRate = asyncCls.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

        entityRun = entityCls.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        entityRunDelayed = entityCls.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
    }

    /**
     * @return {@code true} when running on a Folia server.
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    private static Plugin plugin() {
        return PlayerVaults.getInstance();
    }

    /**
     * Run a task on the next tick of the global region (Folia) or the main thread (Bukkit).
     */
    public static Task run(Runnable runnable) {
        if (FOLIA) {
            return invokeFolia(globalRegionScheduler, globalRun, foliaConsumer(runnable));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin(), runnable));
    }

    /**
     * Run a task after the given delay in ticks, on the global region / main thread.
     */
    public static Task runLater(Runnable runnable, long delayTicks) {
        if (FOLIA) {
            return invokeFolia(globalRegionScheduler, globalRunDelayed, foliaConsumer(runnable), Math.max(1L, delayTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin(), runnable, delayTicks));
    }

    /**
     * Run a repeating task on the global region / main thread. The task body receives its own
     * {@link Task} handle so it can cancel itself.
     */
    public static Task runTimer(Consumer<Task> runnable, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return invokeFolia(globalRegionScheduler, globalRunAtFixedRate,
                    foliaSelfCancelConsumer(runnable), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        }
        return bukkitTimer(runnable, delayTicks, periodTicks, false);
    }

    /**
     * Run a task off the main thread as soon as possible.
     */
    public static Task runAsync(Runnable runnable) {
        if (FOLIA) {
            return invokeFolia(asyncScheduler, asyncRunNow, foliaConsumer(runnable));
        }
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin(), runnable));
    }

    /**
     * Run a task off the main thread after a delay.
     */
    public static Task runLaterAsync(Runnable runnable, long delayTicks) {
        if (FOLIA) {
            return invokeFolia(asyncScheduler, asyncRunDelayed,
                    foliaConsumer(runnable), Math.max(1L, delayTicks) * MS_PER_TICK, TimeUnit.MILLISECONDS);
        }
        return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin(), runnable, delayTicks));
    }

    /**
     * Run a repeating task off the main thread. The task body receives its own {@link Task} handle
     * so it can cancel itself.
     */
    public static Task runTimerAsync(Consumer<Task> runnable, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return invokeFolia(asyncScheduler, asyncRunAtFixedRate, foliaSelfCancelConsumer(runnable),
                    Math.max(1L, delayTicks) * MS_PER_TICK, Math.max(1L, periodTicks) * MS_PER_TICK, TimeUnit.MILLISECONDS);
        }
        return bukkitTimer(runnable, delayTicks, periodTicks, true);
    }

    /**
     * Run a task tied to an entity's region. On Folia the task is scheduled through the
     * entity scheduler (and skipped via {@code retired} if the entity is removed)
     * on Bukkit it runs on the main thread.
     *
     * @param entity  the entity to run the task at
     * @param runnable what to do
     * @param retired  run if the entity has been removed (nullable)
     */
    public static Task runForEntity(Entity entity, Runnable runnable, Runnable retired) {
        if (FOLIA) {
            try {
                Object entityScheduler = entityGetScheduler.invoke(entity);
                Object task = entityRun.invoke(entityScheduler, plugin(), foliaConsumer(runnable), retired);
                return task == null ? null : foliaTask(task);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to schedule Folia entity task", e);
            }
        }
        return wrap(Bukkit.getScheduler().runTask(plugin(), runnable));
    }

    private static Task bukkitTimer(Consumer<Task> body, long delay, long period, boolean async) {
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                body.accept(this::cancel);
            }
        };
        BukkitTask task = async
                ? runnable.runTaskTimerAsynchronously(plugin(), delay, period)
                : runnable.runTaskTimer(plugin(), delay, period);
        return wrap(task);
    }

    private static Task wrap(BukkitTask task) {
        return task::cancel;
    }

    private static Consumer<Object> foliaConsumer(Runnable runnable) {
        return ignored -> runnable.run();
    }

    private static Consumer<Object> foliaSelfCancelConsumer(Consumer<Task> body) {
        return scheduledTask -> body.accept(foliaTask(scheduledTask));
    }

    private static Task foliaTask(Object scheduledTask) {
        return () -> {
            try {
                scheduledTaskCancel.invoke(scheduledTask);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to cancel Folia task", e);
            }
        };
    }

    private static Task invokeFolia(Object scheduler, Method method, Object... args) {
        try {
            Object task = method.invoke(scheduler, prepend(plugin(), args));
            return task == null ? null : foliaTask(task);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to schedule Folia task", e);
        }
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }
}