/*
 *
 *  Copyright 2016 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package how.hollow;

import static how.hollow.producer.util.ScratchPaths.makeProductDir;
import static how.hollow.producer.util.ScratchPaths.makePublishDir;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.HollowProducer.Populator;
import com.netflix.hollow.api.producer.HollowProducer.WriteState;
import com.netflix.hollow.api.producer.HollowProducerListener;

import how.hollow.consumer.infrastructure.FilesystemAnnouncementRetriever;
import how.hollow.consumer.infrastructure.FilesystemBlobRetriever;
import how.hollow.producer.datamodel.Actor;
import how.hollow.producer.datamodel.Movie;
import how.hollow.producer.infrastructure.FilesystemAnnouncer;
import how.hollow.producer.infrastructure.FilesystemPublisher;
import how.hollow.producer.util.DataMonkey;
import how.hollow.producer.util.StandardStreamsLogger;

public class CyclicProducer {
    public static void main(String args[]) throws InterruptedException {

        String namespace = args.length == 0 ? "cyclic" : args[0];

        /// 1. Start the producer; observe cycles running on a 10 second cadence

        CyclicProducer myProducer = new CyclicProducer(namespace);

        myProducer.initializeDataModel(Movie.class);
        myProducer.restore();

        /// 2. Hollow only produces new data states when there are actual changes.
        ///    Try using the debugger to change a name or a title on the fly

        myProducer.cycleForever(new Populator(){
            @Override
            public void populate(WriteState newState) {

                /// 2b. uncomment the `dataMonkey` line below to simulate source data changes automatically

                /// DataMonkey(TM) for demonstration purposes only; do NOT taunt DataMonkey
                //newState = dataMonkey.introduceChaos(newState);

                {
                    Set<Actor> cast = new HashSet<>();
                    cast.add(new Actor(263, "Henry Thomas"));
                    cast.add(new Actor(337, "Drew Barrymore"));
                    Movie movie = new Movie(37, "E.T. the Extra-Terrestrial", 1982, cast);

                    newState.add(movie);
                }
                {
                    Set<Actor> cast = new HashSet<>();
                    cast.add(new Actor(337, "Drew Barrymore"));
                    Movie movie = new Movie(193, "Firestarter", 1984, cast);

                    newState.add(movie);
                }
                {
                    Set<Actor> cast = new HashSet<>();
                    cast.add(new Actor(2777, "Finn Wolfhard"));
                    cast.add(new Actor(11, "Millie Bobby Brown"));
                    cast.add(new Actor(953, "Gaten Matarazzo"));
                    cast.add(new Actor(3137, "Caleb McLaughlin"));
                    Movie movie = new Movie(1987, "Stranger Things Season 1", 2016, cast);

                    newState.add(movie);
                }
            }
        });

        /*
         * UP NEXT: `SourceDataProducer` demonstrates producing data states from an external source of truth
         *
         * BONUS: create a `MessageDrivenProducer` that runs a cycle in response to receiving a message,
         *        such as from Kafka
         */
    }

    CyclicProducer(String namespace) {
        final Path productDir = makeProductDir(namespace);
        final Path publishDir = makePublishDir(namespace);

        HollowProducerListener logger = new StandardStreamsLogger(){
            @Override public void onProducerInit(long elapsed, TimeUnit unit) {
                info("I AM THE PRODUCER\n  PRODUCING IN  %s\n  PUBLISHING TO %s\n", productDir, publishDir);
                super.onProducerInit(elapsed, unit);
            }
        };

        HollowProducer hollowProducer = new HollowProducer(
                new FilesystemPublisher(productDir, publishDir),
                new FilesystemAnnouncer(publishDir),
                new FilesystemBlobRetriever(publishDir));
        hollowProducer.addListener(logger);


        this.hollowProducer = hollowProducer;
        this.announcementRetriever = new FilesystemAnnouncementRetriever(publishDir);
    }

    void initializeDataModel(Class<?>...classes) {
        hollowProducer.initializeDataModel(classes);
    }

    void restore() {
        hollowProducer.restore(announcementRetriever);
    }

    void cycleForever(HollowProducer.Populator task) {
        long lastCycleTime = Long.MIN_VALUE;
        while(true) {
            waitForMinCycleTime(lastCycleTime);
            lastCycleTime = System.currentTimeMillis();
            hollowProducer.runCycle(task);
        }
    }

    @SuppressWarnings("unused")
    private static final DataMonkey dataMonkey = new DataMonkey();
    private static final long MIN_TIME_BETWEEN_CYCLES = SECONDS.toMillis(10);

    private final HollowProducer hollowProducer;
    private final HollowConsumer.AnnouncementRetriever announcementRetriever;

    private void waitForMinCycleTime(long lastCycleTime) {
        long targetNextCycleTime = lastCycleTime + MIN_TIME_BETWEEN_CYCLES;

        while(System.currentTimeMillis() < targetNextCycleTime) {
            try {
                Thread.sleep(targetNextCycleTime - System.currentTimeMillis());
            } catch(InterruptedException ignore) { }
        }
    }
}
