package org.scriptella.mongodb.statement;

import com.mongodb.util.JSON;
import com.mongodb.util.JSONCallback;
import com.mongodb.util.JSONParseException;
import org.bson.BSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scriptella.mongodb.MongoDbProviderException;
import org.scriptella.mongodb.operation.MongoOperation;
import scriptella.expression.Expression;
import scriptella.spi.ParametersCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Mongodb statements in JSON(BSON) format
 *
 * Support "?variable / $variable" or "?{variable or JEXL expression} ${variable or JEXL expression}" syntax.
 *
 * Note:
 * 1. ? prefixed expressions are not substituted inside quotes, comments and outside of query/script elements; http://scriptella.org/reference/index.html#Expressions+and+Variables+Substitution
 * 2. This class is not thread-safe!
 *
 * @author:
 * Fyodor Kupolov <scriptella@gmail.com>
 * Mark Luo <luogenghui@gmail.com>
 */
public class JsonStatementsParser {

    private List<MongoOperation> operations;
    private MongoDbTypesConverter typesConverter;

    public JsonStatementsParser(MongoDbTypesConverter typesConverter) {
        this.typesConverter = typesConverter;
    }

    public void parse(String json, final ParametersCallback params) throws MongoDbProviderException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String,Object> result = mapper.readValue(json, HashMap.class);
            evaluateExpression(result, params);

            BSONObject bson = (BSONObject) JSON.parse(mapper.writeValueAsString(result));
            //BSONObject bson = new BasicBSONObject();
            //bson.putAll(result);
            System.out.println(String.format("%s parsed result: %s",this.getClass().getSimpleName(),bson.toString()));

            operations = new ArrayList<MongoOperation>();
            if (bson instanceof List) {
                List<?> list = (List<?>) bson;
                for (Object o : list) {
                    if (o instanceof BSONObject) {
                        operations.add(MongoOperation.from((BSONObject) o));
                    } else {
                        throw new MongoDbProviderException("A document was expected in the array of operation, but was " + o);
                    }
                }
            } else {
                operations.add(MongoOperation.from(bson));

            }
        } catch (Exception e) {
            throw new MongoDbProviderException("Unable to parse JSON statement", e);
        }
    }

    void evaluateExpression(Map<String,Object> m, final ParametersCallback params) {
        for (String key : m.keySet()) {
            if ( m.get(key) instanceof String) {
                String value = m.get(key).toString();
                // if expression
                if (value.startsWith("?") || value.startsWith("$")) {
                    ExpressionMatcher matcher = new ExpressionMatcher();
                    matcher.reset(value);
                    matcher.setFrom(1);
                    if (matcher.matches()) {
                        m.put(key,Expression.compile(matcher.getMatchString()).evaluate(params));
                    }
                }
            } else {
                evaluateExpression((Map<String,Object>) m.get(key), params);
            }
        }

    }

    public List<MongoOperation> getOperations() {
        return operations;
    }
}
