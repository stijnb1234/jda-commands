package com.github.kaktushose.jda.commands.dispatching.filter.impl;

import com.github.kaktushose.jda.commands.dispatching.CommandContext;
import com.github.kaktushose.jda.commands.dispatching.filter.Filter;
import com.github.kaktushose.jda.commands.reflect.CommandDefinition;
import com.github.kaktushose.jda.commands.reflect.CooldownDefinition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A {@link Filter} implementation that contains the business logic behind command cooldowns.
 * If the command isn't annotated with {@link com.github.kaktushose.jda.commands.annotations.Cooldown Cooldown} or more
 * formally if the {@link CommandDefinition} doesn't hold a {@link CooldownDefinition} or the delay of the
 * {@link CooldownDefinition} amounts to {@code 0} this filter has no effect.
 *
 * @author Kaktushose
 * @version 2.0.0
 * @see com.github.kaktushose.jda.commands.annotations.Cooldown
 * @since 2.0.0
 */
public class CooldownFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CooldownFilter.class);
    private final Map<Long, Set<CooldownEntry>> activeCooldowns;

    public CooldownFilter() {
        activeCooldowns = new HashMap<>();
    }

    /**
     * Checks if an active cooldown for the given {@link CommandDefinition} exists and will eventually cancel the
     * context.
     *
     * @param context the {@link CommandContext} to filter
     */
    @Override
    public void apply(@NotNull CommandContext context) {
        CommandDefinition command = context.getCommand();

        if (!command.hasCooldown()) {
            return;
        }

        long id = context.getEvent().getAuthor().getIdLong();

        activeCooldowns.putIfAbsent(id, new HashSet<>());

        Optional<CooldownEntry> optional = activeCooldowns.get(id).stream().filter(entry -> entry.command.equals(command)).findFirst();

        if (optional.isPresent()) {
            CooldownEntry entry = optional.get();
            long remaining = entry.duration - (System.currentTimeMillis() - entry.startTime);
            if (remaining <= 0) {
                activeCooldowns.get(id).remove(entry);
            } else {
                context.setCancelled(true);
                context.setErrorMessage(context.getImplementationRegistry().getErrorMessageFactory().getCooldownMessage(context, remaining));
                log.debug("Command has a remaining cooldown of {} ms!", remaining);
                return;
            }
        }

        CooldownDefinition cooldown = command.getCooldown();
        long startTime = System.currentTimeMillis();
        long duration = cooldown.getTimeUnit().toMillis(cooldown.getDelay());
        activeCooldowns.get(id).add(new CooldownEntry(command, startTime, duration));
        log.debug("Added new cooldown entry for this user");
    }

    private static class CooldownEntry {
        private final CommandDefinition command;
        private final long startTime;
        private final long duration;

        public CooldownEntry(CommandDefinition command, long startTime, long duration) {
            this.command = command;
            this.startTime = startTime;
            this.duration = duration;
        }

        public CommandDefinition getCommand() {
            return command;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDuration() {
            return duration;
        }
    }
}
