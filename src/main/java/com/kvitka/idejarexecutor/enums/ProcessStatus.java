package com.kvitka.idejarexecutor.enums;

public enum ProcessStatus {
    INIT,

    MAVEN_TEMPLATE_CREATING,
    MAVEN_TEMPLATE_CREATING_FAILED(true),
    MAVEN_TEMPLATE_CREATED,

    PROJECT_FILES_CREATING,
    PROJECT_FILES_CREATING_FAILED(true),
    PROJECT_FILES_CREATED,

    MAVEN_JAR_CREATING,
    MAVEN_JAR_CREATING_FAILED(true),
    MAVEN_JAR_CREATED,

    DOCKER_FILES_CREATING,
    DOCKER_DEPLOYING,
    DOCKER_DEPLOYING_FAILED(true),
    DOCKER_RUNNING,

    USER_INTERRUPT(true),
    PROGRAM_FINISHED(true);

    private final boolean finished;

    ProcessStatus() {
        this.finished = false;
    }

    ProcessStatus(boolean finished) {
        this.finished = finished;
    }

    public boolean isFinished() {
        return finished;
    }
}
