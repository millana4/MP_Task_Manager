package com.mercator.taskmanager.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Строит ключи Redis-списков из префиксов (application.yaml) и кода гео.
 * Ключи списков вида "<prefix>:<geo>", например "parse:tasks:spb".
 * Код гео берём из GeoTopics.codeOf.
 */
@Component
public class RedisKeys {

    private final String parseTasksPrefix;
    private final String parseResultsPrefix;
    private final String selectTasksPrefix;
    private final String selectResultsPrefix;

    public RedisKeys(
            @Value("${taskmanager.redis.parse-tasks-prefix}") String parseTasksPrefix,
            @Value("${taskmanager.redis.parse-results-prefix}") String parseResultsPrefix,
            @Value("${taskmanager.redis.select-tasks-prefix}") String selectTasksPrefix,
            @Value("${taskmanager.redis.select-results-prefix}") String selectResultsPrefix) {
        this.parseTasksPrefix = parseTasksPrefix;
        this.parseResultsPrefix = parseResultsPrefix;
        this.selectTasksPrefix = selectTasksPrefix;
        this.selectResultsPrefix = selectResultsPrefix;
    }

    public String parseTasks(String geo)   { return parseTasksPrefix   + ":" + GeoTopics.codeOf(geo); }
    public String parseResults(String geo) { return parseResultsPrefix + ":" + GeoTopics.codeOf(geo); }
    public String selectTasks(String geo)  { return selectTasksPrefix  + ":" + GeoTopics.codeOf(geo); }
    public String selectResults(String geo){ return selectResultsPrefix + ":" + GeoTopics.codeOf(geo); }
}
