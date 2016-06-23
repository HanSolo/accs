/*
 * Copyright (c) 2016 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.accs;

import javafx.application.Platform;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


/**
 * Created by hansolo on 23.06.16.
 */
public enum Cleaner {
    INSTANCE;

    private static final long                     TWO_DAYS = 172800;
    private volatile     ScheduledFuture<?>       cleanerTask;
    private static       ScheduledExecutorService periodicCleanerExecutorService;
    private static       boolean                  started = false;

    Cleaner() {
    }

    public void start() {
        if (!started) {
            scheduleCleanerTask();
            cleanupLocations();
            started = true;
        }
    }

    private void cleanupLocations() {
        JSONArray locations = RestClient.INSTANCE.getAllLocations();
        for (Object obj : locations) {
            Location location = new Location(((JSONObject) obj));
            if (location.timestamp.getEpochSecond() < Instant.now().getEpochSecond() - TWO_DAYS) RestClient.INSTANCE.deleteLocation(location);
        }
    }


    // ******************** Scheduled task related ****************************
    private synchronized static void enableCleanerExecutorService() {
        if (null == periodicCleanerExecutorService) {
            periodicCleanerExecutorService = new ScheduledThreadPoolExecutor(1, getThreadFactory("CleanerTask", false));
        }
    }
    private synchronized void scheduleCleanerTask() {
        enableCleanerExecutorService();
        stopTask(cleanerTask);
        cleanerTask = periodicCleanerExecutorService.scheduleAtFixedRate(() -> Platform.runLater(() -> cleanupLocations()), 1, 24, TimeUnit.HOURS);
    }

    private static ThreadFactory getThreadFactory(final String THREAD_NAME, final boolean IS_DAEMON) {
        return runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(IS_DAEMON);
            return thread;
        };
    }

    private void stopTask(ScheduledFuture<?> task) {
        if (null == task) return;

        task.cancel(true);
        task = null;
    }
}
