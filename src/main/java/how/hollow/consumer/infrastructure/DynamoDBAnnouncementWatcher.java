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
package how.hollow.consumer.infrastructure;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.HollowConsumer.AnnouncementWatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DynamoDBAnnouncementWatcher implements AnnouncementWatcher {

    private final DynamoDB dynamoDB;
    private final String tableName;
    private final String blobNamespace;
    
    private final List<HollowConsumer> subscribedConsumers;

    private long latestVersion;

    public DynamoDBAnnouncementWatcher(AWSCredentials credentials, String tableName, String blobNamespace) {
        this.dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).build());
        this.tableName = tableName;
        this.blobNamespace = blobNamespace;
        this.subscribedConsumers = Collections.synchronizedList(new ArrayList<HollowConsumer>());
        
        this.latestVersion = readLatestVersion();
        
        setupPollingThread();
    }
    
    public void setupPollingThread() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        long currentVersion = readLatestVersion();
                        if (latestVersion != currentVersion) {
                            latestVersion = currentVersion;
                            for(HollowConsumer consumer : subscribedConsumers)
                                consumer.triggerAsyncRefresh();
                        }

                        Thread.sleep(1000);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        });

        t.setName("hollow-dynamodb-announcementwatcher-poller");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public long getLatestVersion() {
        return latestVersion;
    }

    @Override
    public void subscribeToUpdates(HollowConsumer consumer) {
        subscribedConsumers.add(consumer);
    }

    public long readLatestVersion() {
        Table table = dynamoDB.getTable(tableName);

        Item item = table.getItem("namespace", blobNamespace,
                "version, pin_version", null);

        if (item.isPresent("pin_version") && !item.isNull("pin_version"))
            return item.getLong("pin_version");

        return item.getLong("version");
    }
}
