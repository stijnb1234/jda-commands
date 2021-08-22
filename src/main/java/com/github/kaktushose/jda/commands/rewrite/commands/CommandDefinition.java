package com.github.kaktushose.jda.commands.rewrite.commands;

import com.github.kaktushose.jda.commands.annotations.Command;
import com.github.kaktushose.jda.commands.annotations.CommandController;
import com.github.kaktushose.jda.commands.annotations.Cooldown;
import com.github.kaktushose.jda.commands.annotations.Permission;
import com.github.kaktushose.jda.commands.entities.CommandEvent;
import com.github.kaktushose.jda.commands.exceptions.CommandException;
import com.github.kaktushose.jda.commands.rewrite.parameter.ParameterDefinition;
import com.github.kaktushose.jda.commands.rewrite.parameter.adapter.ParameterAdapterRegistry;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class CommandDefinition {

    private static final Logger log = LoggerFactory.getLogger(CommandDefinition.class);
    private final List<String> labels;
    private final CommandMetadata metadata;
    private final List<ParameterDefinition> parameters;
    private final Set<String> permissions;
    private final CooldownDefinition cooldown;
    private final boolean isSuper;
    private final boolean isDM;
    private final Method method;
    private final Object instance;

    private CommandDefinition(List<String> labels,
                              CommandMetadata metadata,
                              List<ParameterDefinition> parameters,
                              Set<String> permissions,
                              CooldownDefinition cooldown,
                              boolean isSuper,
                              boolean isDM,
                              Method method,
                              Object instance) {
        this.labels = labels;
        this.metadata = metadata;
        this.parameters = parameters;
        this.permissions = permissions;
        this.cooldown = cooldown;
        this.isSuper = isSuper;
        this.isDM = isDM;
        this.method = method;
        this.instance = instance;
    }

    public static Optional<CommandDefinition> build(Method method, Object instance, ParameterAdapterRegistry registry) {
        Command command = method.getAnnotation(Command.class);
        CommandController commandController = method.getDeclaringClass().getAnnotation(CommandController.class);

        if (!command.isActive()) {
            log.debug("Command {} is set inactive. Skipping this command", method.getName());
            return Optional.empty();
        }

        Set<String> permissions = new HashSet<>();
        if (method.isAnnotationPresent(Permission.class)) {
            Permission permission = method.getAnnotation(Permission.class);
            permissions = Sets.newHashSet(permission.value());
        }

        // generate possible labels
        List<String> labels = new ArrayList<>();
        for (String controllerLabel : commandController.value()) {
            for (String commandLabel : command.value()) {
                String label = (controllerLabel + " " + commandLabel).trim();
                labels.add(label);
            }
        }

        // build parameter definitions
        List<ParameterDefinition> parameters = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            parameters.add(ParameterDefinition.build(parameter));
        }

        // validate parameter definitions
        boolean hasOptional = false;
        for (int i = 0; i < parameters.size(); i++) {
            ParameterDefinition parameter = parameters.get(i);
            Class<?> type = parameter.getType();

            // first argument must be a CommandEvent
            if (i == 0) {
                if (!type.isAssignableFrom(CommandEvent.class)) {
                    logError(String.format("First parameter must be of type %s!", CommandEvent.class.getName()), method);
                    return Optional.empty();
                }
                continue;
            }

            // check if parameter adapter exists
            if (!registry.exists(type)) {
                logError(String.format("No parameter adapter for %s found!", type.getName()), method);
                return Optional.empty();
            }

            // argument parsing can be skipped by using just a String array (the traditional way of command frameworks)
            // this means that no other parameters are allowed in this case
            if (type.isAssignableFrom(String[].class)) {
                if (parameters.size() > 2) {
                    logError("Additional parameters aren't allowed when using arrays!", method);
                    return Optional.empty();
                }
            }

            // String concatenation is enabled
            if (parameter.isConcat()) {
                // must be last parameter
                if (i != parameters.size() - 1) {
                    logError("Concatenation may be only enabled for the last parameter", method);
                    return Optional.empty();
                }
                // must be a String
                if (!type.isAssignableFrom(String.class)) {
                    logError("Concatenation is enabled but last parameter is not a String!", method);
                    return Optional.empty();
                }
            }

            // if method already had an optional parameter (hasOptional == true) this one has to be optional as well
            if (hasOptional && !parameter.isOptional()) {
                logError("An optional parameter must not be followed by a non-optional parameter!", method);
                return Optional.empty();
            }
            if (parameter.isOptional()) {
                // using primitives with default values results in NPEs. Warn the user about it
                if (parameter.getDefaultValue().equals("") && parameter.isPrimitive()) {
                    log.warn("Command {} has an optional primitive datatype parameter, but no default value is present! " +
                            "This will result in a NullPointerException if the command is executed without the optional parameter!", method.getName());
                }
                hasOptional = true;
            }
        }

        return Optional.of(new CommandDefinition(
                labels,
                CommandMetadata.build(command, commandController),
                parameters,
                permissions,
                CooldownDefinition.build(method.getAnnotation(Cooldown.class)),
                command.isSuper(),
                command.isDM(),
                method,
                instance
        ));
    }

    private static void logError(String message, Method commandMethod) {
        log.error("An error has occurred! Skipping Command {}!",
                commandMethod.getName(),
                new CommandException("Command method has an invalid method signature! " + message));
    }

    public List<String> getLabels() {
        return labels;
    }

    public CommandMetadata getMetadata() {
        return metadata;
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public CooldownDefinition getCooldown() {
        return cooldown;
    }

    public boolean isSuper() {
        return isSuper;
    }

    public boolean isDM() {
        return isDM;
    }

    public Method getMethod() {
        return method;
    }

    public Object getInstance() {
        return instance;
    }
}
