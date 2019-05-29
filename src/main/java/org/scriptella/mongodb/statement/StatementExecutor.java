package org.scriptella.mongodb.statement;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.scriptella.mongodb.MongoDbProviderException;
import org.scriptella.mongodb.bridge.MongoBridge;
import org.scriptella.mongodb.operation.MongoOperation;
import scriptella.spi.ParametersCallback;
import scriptella.spi.QueryCallback;
import scriptella.spi.Resource;
import scriptella.util.IOUtils;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author: Fyodor Kupolov, Mark Luo
 * @version 1.1
 */
public class StatementExecutor implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(StatementExecutor.class.getName());
    private static final boolean DEBUG = LOG.isLoggable(Level.FINE);
    private Map<Resource, JsonStatementsParser> cache = new IdentityHashMap<Resource, JsonStatementsParser>();
    private MongoBridge bridge;
    private MongoDbTypesConverter mongoDbTypesConverter = new MongoDbTypesConverter();

    StatementExecutor() {
    }

    public StatementExecutor(MongoBridge bridge) {
        this.bridge = bridge;
    }

    public void executeScript(Resource resource, ParametersCallback parametersCallback) {
        JsonStatementsParser statement = compile(resource, parametersCallback);
        List<MongoOperation> operations = statement.getOperations();
        for (MongoOperation operation : operations) {
            if (DEBUG) {
                LOG.fine("Executing operation " + operation);
            }
            operation.executeScript(bridge);
        }
    }

    public void executeQuery(Resource resource, ParametersCallback parametersCallback, QueryCallback queryCallback) {
        JsonStatementsParser statement = compile(resource, parametersCallback);
        List<MongoOperation> operations = statement.getOperations();
        for (MongoOperation operation : operations) {
            if (DEBUG) {
                LOG.fine("Executing query " + operation);
            }
            DBCursor cursor = operation.executeQuery(bridge);
            DBObjectParametersCallback row = new DBObjectParametersCallback(parametersCallback);
            try {
                for (DBObject dbObject : cursor) {
                    row.setObject(dbObject);
                    queryCallback.processRow(row);
                }
            } finally {
                IOUtils.closeSilently(cursor);
            }
        }
    }

    JsonStatementsParser compile(Resource resource, ParametersCallback parametersCallback) {
        JsonStatementsParser statement = cache.get(resource);
        if (statement == null) {
            try {
                statement = new JsonStatementsParser(mongoDbTypesConverter);
                statement.parse(IOUtils.toString(resource.open()), parametersCallback);

                if (DEBUG) {
                    LOG.fine("Compiled statement " + statement);
                }
            } catch (IOException e) {
                throw new MongoDbProviderException("Failed to read JSON resource", e);
            }
            cache.put(resource, statement);
        }
        return statement;
    }

    @Override
    public void close() {
        if (bridge != null) {
            cache.clear();
            bridge.close();
            bridge = null;
        }
    }
}
