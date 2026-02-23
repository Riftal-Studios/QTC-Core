package com.riftalstudios.qtccore.jobs;

public enum JobCategory {
    BOSS_KILL(25),
    RESOURCE_GATHERING(15),
    BUILDING(20),
    ESCORT(10),
    TEACHING(15),
    CUSTOM(50);

    private final int maxPoints;

    JobCategory(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int getMaxPoints() {
        return maxPoints;
    }
}
