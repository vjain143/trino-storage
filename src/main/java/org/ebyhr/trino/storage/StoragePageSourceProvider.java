/*
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
package org.ebyhr.trino.storage;

import com.google.inject.Inject;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorPageSource;
import io.trino.spi.connector.ConnectorPageSourceProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedPageSource;
import io.trino.spi.connector.RecordPageSource;
import io.trino.spi.connector.RecordSet;
import org.ebyhr.trino.storage.operator.FilePlugin;
import org.ebyhr.trino.storage.operator.PluginFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class StoragePageSourceProvider
        implements ConnectorPageSourceProvider
{
    private final StorageClient storageClient;
    private final StorageRecordSetProvider recordSetProvider;

    @Inject
    public StoragePageSourceProvider(StorageClient storageClient, StorageRecordSetProvider recordSetProvider)
    {
        this.storageClient = requireNonNull(storageClient, "storageClient is null");
        this.recordSetProvider = requireNonNull(recordSetProvider, "recordSetProvider is null");
    }

    @Override
    public ConnectorPageSource createPageSource(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorSplit split,
            ConnectorTableHandle table,
            List<ColumnHandle> columns,
            DynamicFilter dynamicFilter)
    {
        StorageSplit storageSplit = (StorageSplit) requireNonNull(split, "split is null");

        String schemaName = storageSplit.getSchemaName();
        String tableName = storageSplit.getTableName();
        FilePlugin plugin = PluginFactory.create(schemaName);

        if (plugin.usesPages()) {
            InputStream inputStream;
            try {
                inputStream = storageClient.getInputStream(session, tableName);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new FixedPageSource(plugin.getPagesIterator(inputStream));
        }

        RecordSet recordSet = recordSetProvider.getRecordSet(transaction, session, split, table, columns);
        return new RecordPageSource(recordSet);
    }
}
