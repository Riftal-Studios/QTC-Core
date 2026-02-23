package com.riftalstudios.qtccore.events;

import com.riftalstudios.qtccore.jobs.Job;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class JobCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Job job;

    public JobCompletedEvent(Job job) {
        this.job = job;
    }

    public Job getJob() { return job; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
