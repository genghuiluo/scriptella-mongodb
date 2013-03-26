package org.scriptella.mongodb.statement;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.bson.BSONObject;
import org.scriptella.mongodb.statement.operation.DbCollectionFind;
import org.scriptella.mongodb.statement.operation.DbCollectionSave;
import org.scriptella.mongodb.statement.operation.DbRunCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents MongoDB command or method.
 *
 * @author Fyodor Kupolov <scriptella@gmail.com>
 */
public abstract class MongoOperation {

    public static final String OPERATION_PARAM = "operation";
    public static final String COLLECTION_PARAM = "collection";
    public static final String DATA_PARAM = "data";
    /**
     * Executes DB command
     */
    public static String NAME = "db.runCommand";


    /**
     * Represents JSON document which can be executed as find or update depending on the context.
     */
    public static String RUN_JSON_DOC = "scriptella.document";

    private String collectionName;
    List<Object> arguments;

    public MongoOperation(List<Object> arguments) {
        this(null, arguments);
    }

    public MongoOperation(String collectionName, List<Object> arguments) {
        this.collectionName = collectionName;
        this.arguments = Collections.unmodifiableList(arguments);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    /**
     * the first argument cast to {@link BSONObject}. Can be used to avoid boilerplate code when operation is known to have only one argument of {@link BSONObject}.
     *
     * @return the first argument cast to {@link BSONObject}.
     */
    @SuppressWarnings("unchecked")
    public <T extends BSONObject> T getFirstArgumentAsBson() {
        return (T) arguments.get(0);
    }

    public abstract void executeScript(MongoBridge mongoBridge);

    public abstract DBCursor executeQuery(MongoBridge mongoBridge);


    @Override
    public String toString() {
        return "MongoOperation{" +
                ", collectionName='" + collectionName + '\'' +
                ", arguments=" + arguments +
                '}';
    }

    public static MongoOperation from(BSONObject object) {
        //TODO For now only #RUN_JSON_DOC is hardcoded. This has to be extended to check for $scriptellaMethod JSON extension
        Object name = object.get(OPERATION_PARAM);
        Object collection = object.get(COLLECTION_PARAM);
        Object data = object.get(DATA_PARAM);
        if ("db.collection.find".equals(name)) {
            return new DbCollectionFind((String) collection, (DBObject) data);
        } else if ("db.runCommand".equals(name)) {
            return new DbRunCommand((DBObject) data);
        } else if ("db.collection.save".equals(name)) {
            return new DbCollectionSave((String) collection, Arrays.asList(data));
        }
        throw new UnsupportedOperationException("Operation " + name + " is not supported");

    }
}
