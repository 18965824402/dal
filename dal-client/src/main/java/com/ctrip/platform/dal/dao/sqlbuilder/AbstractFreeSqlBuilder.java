package com.ctrip.platform.dal.dao.sqlbuilder;

import static com.ctrip.platform.dal.dao.helper.DalShardingHelper.buildShardStr;
import static com.ctrip.platform.dal.dao.helper.DalShardingHelper.isTableShardingEnabled;
import static com.ctrip.platform.dal.dao.helper.DalShardingHelper.locateTableShardId;
import static com.ctrip.platform.dal.dao.sqlbuilder.AbstractSqlBuilder.wrapField;
import static com.ctrip.platform.dal.dao.sqlbuilder.MeltdownHelper.meltdown;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.ctrip.platform.dal.common.enums.DatabaseCategory;
import com.ctrip.platform.dal.dao.DalClientFactory;
import com.ctrip.platform.dal.dao.DalHints;
import com.ctrip.platform.dal.dao.StatementParameters;
import com.ctrip.platform.dal.dao.sqlbuilder.Expressions.Expression;

/**
 * This sql builder only handles template creation. It will not do with the parameters
 * for now.
 * 
 * rules:
 * if bracket has no content, bracket will be removed
 * expression can be evaluated and can be wrapped by bracket and connect to each other by and/or
 * expression should have no leading and tailing and/or, it there is, the and/or will be removed during validating
 * 
 * To be align with FreeSelectSqlBuilder and FreeUpdateSqlBuilder, this class will not handle methods that dealing with 
 * some parameter. To deal with parameter, you should use Expressions.*
 * 
 * @author jhhe
 *
 */
public class AbstractFreeSqlBuilder implements SqlBuilder {
    /**
     * Because builder will not append space before and after ( or ), 
     * you can append EMPTY to bypass this restriction. 
     */
    public static final String EMPTY = "";
    public static final String PLACE_HOLDER = "?";
    
    /**
     * Builder will not insert space before COMMA.
     */
    public static final Text COMMA = text(",");
    public static final Text SPACE = text(" ");
    public static final Text SELECT = text("SELECT");
    public static final Text FROM = text("FROM");
    public static final Text WHERE= text("WHERE");
    public static final Text AS = text("AS");
    public static final Text ORDER_BY = text("ORDER BY");
    public static final Text ASC = text("ASC");
    public static final Text DESC = text("DESC");
    public static final Text GROUP_BY = text("GROUP BY");
    public static final Text HAVING = text("HAVING");
    
    private boolean enableAutoMeltdown = true;
    private BuilderContext context = new BuilderContext();
    private ClauseList clauses = new ClauseList();
    private MeltdownHelper meltdownHelper = new MeltdownHelper ();
    
    public AbstractFreeSqlBuilder() {
        context = new BuilderContext();
        clauses = new ClauseList();
        clauses.setContext(context);
    }
    
    /**
     * In case there is Table clause appended, logic DB must be set to determine
     * if the table name can be sharded or not. Set this logic db name will also
     * set db category identified by the logic db name. So you don't need to set
     * db category again.
     * 
     * @param logicDbName
     * @return
     */
    public AbstractFreeSqlBuilder setLogicDbName(String logicDbName) {
        context.setLogicDbName(logicDbName);
        return this;
    }
    
    public DatabaseCategory getDbCategory() {
        return context.getDbCategory();
    }
    
    /**
     * If you already set logic db name, then you don't need to set this.
     *  
     * @param dbCategory
     * @return
     */
    public AbstractFreeSqlBuilder setDbCategory(DatabaseCategory dbCategory) {
        context.setDbCategory(dbCategory);
        return this;
    }
    
    public AbstractFreeSqlBuilder setHints(DalHints hints) {
        context.setHints(hints);
        return this;
    }
    
    /**
     * Specify parameters that come with this builder
     * @param parameters
     * @return
     */
    public AbstractFreeSqlBuilder with(StatementParameters parameters) {
        context.setParameters(parameters);
        return this;
    }
    
    /**
     * Build the final sql.
     * 
     * It will append where and check if the value is start of "and" or "or", of so, the leading 
     * "and" or "or" will be removed.
     */
    @SuppressWarnings("unchecked")
    public String build() {
        try {
            List<Clause> clauseList = clauses.list;

            if(enableAutoMeltdown)
                clauseList = (List<Clause>)meltdown(clauseList);
            
            return finalBuild(clauseList);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public StatementParameters buildParameters() {
        return context.getParameters();
    }
    
    /**
     * Diable the auto removal of AND, OR, NOT, (, ) and nullable expression
     */
    public void disableAutoMeltdown() {
        enableAutoMeltdown = false;
    }
    
    public static Text text(Object template) {
        return new Text(template.toString());
    }
    
    /**
     * Create Column clause with given name
     * @param columnName
     * @return
     */
    public static Column column(String columnName) {
        return new Column(columnName);
    }
    
    /**
     * Create Table clause with given name
     * @param tableName
     * @return
     */
    public static Table table(String tableName) {
        return new Table(tableName);
    }
    
    /**
     * Basic append methods definition
     */
    
    /**
     * Basic append method. Parameter value can be String, Clause or Object. It will allow the maximal
     * flexibility for input parameter.
     * 
     * @param template
     * @return builder itself
     */
    public AbstractFreeSqlBuilder append(Object template) {
        Objects.requireNonNull(template, "Parameter template should be type of String, Clause, or Object, exceptnull.");

        if(template instanceof String) {
            clauses.add(new Text((String)template));
        } else if(template instanceof ClauseList) {
            for(Clause c: ((ClauseList)template).list)
                clauses.add(c);
        } else if(template instanceof Clause) {
            clauses.add((Clause)template);
        } else {
            clauses.add(new Text(template.toString()));
        }

        return this;
    }
    
    /**
     * Append multiple template to the builder. Parameter of String will be append as Text.
     *  
     * If used with Expressions static methods, you can build
     * sql in a very flexible way. Usage like:
     * 
     * append(
     *          "orderId > ?"
     *          AND,
     *          leftBracket,
     *          NOT, equals("Abc"),
     *          expression("count(1)"),
     *          rightBracket,
     *          OR,
     *          ...
     *       )
     * @param templates
     * @return
     */
    public AbstractFreeSqlBuilder append(Object... templates) {
        for(Object template: templates)
            append(template);
        return this;
    }
    
    /**
     * Append when the condition is met
     * @param condition
     * @param template
     * @return
     */
    public AbstractFreeSqlBuilder appendWhen(boolean condition, Object template) {
        return condition ? append(template): this;
    }
    
    /**
     * Append template depends on whether the condition is met.
     * @param condition
     * @param template value to be appended when condition is true
     * @param elseTemplate value to be appended when condition is true
     * @return
     */
    public AbstractFreeSqlBuilder appendWhen(boolean condition, Object template, Object elseTemplate) {
        return condition ? append(template): append(elseTemplate);
    }
    
    /**
     * Append as column. The column name will be quoted by database specific char.
     * 
     * @param columnNames 
     * @return
     */
    public AbstractFreeSqlBuilder appendColumn(String columnName) {
        return append(column(columnName));
    }
    
    /**
     * Append as column with alias. The column name will be quoted by database specific char.
     * 
     * @param columnNames 
     * @param alias
     * @return
     */
    public AbstractFreeSqlBuilder appendColumn(String columnName, String alias) {
        return append(column(columnName).as(alias));
    }
    
    /**
     * Append as Table. Same as append(table(tableName)).
     * 
     * The tableName will be replaced by true table name if it is a logic table that allow shard.
     * 
     * @param tableName table name. The table can be sharded
     * @return
     */
    public AbstractFreeSqlBuilder appendTable(String tableName) {
        return append(table(tableName));
    }
    
    /**
     * Append as Table with alias. Same as append(table(tableName)).
     * 
     * The tableName will be replaced by true table name if it is a logic table that allow shard.
     * 
     * @param tableName table name. The table can be sharded
     * @param alias
     * @return
     */
    public AbstractFreeSqlBuilder appendTable(String tableName, String alias) {
        return append(table(tableName).as(alias));
    }
    
    /**
     * Append as Expression. Same as append(expression(expression))
     * 
     * @param expression
     * @return
     */
    public AbstractFreeSqlBuilder appendExpression(String expression) {
        return append(new Expression(expression));
    }
    
    /**
     * Append multiple expressions. Same as append(Object..values) except all 
     * String parameters will be wrapped by Expression instead of Text.
     * 
     * Note: The String parameter will be wrapped by Expression clause.
     * 
     * @param expressions
     * @return
     */
    public AbstractFreeSqlBuilder appendExpressions(Object...expressions) {
        for(Object expr: expressions) {
            if(expr instanceof String) {
                appendExpression((String)expr);
            }else {
                append(expr);
            }
        }

        return this;
    }
    
    /**
     * Combined append for SELECT
     */

    /**
     * Build a SELECT column1, column2,...using the giving columnNames
     * 
     * Note: The String parameter will be wrapped by Column clause.
     * 
     * @param columns The type of column can be Column or other clause
     * @param table
     * @return
     */
    public AbstractFreeSqlBuilder select(Object... columnNames) {
        append(SELECT);
        for (int i = 0; i < columnNames.length; i++) {
            if(columnNames[i] instanceof String) {
                appendColumn((String)columnNames[i]);
            }else{
                append(columnNames[i]);
            }
            if(i != columnNames.length -1)
                append(COMMA);    
        }

        return this;
    }
    
    /**
     * Append FROM and table for SELECT statement. And if logic DB is sql server, it will 
     * append "WITH (NOLOCK)" by default 
     * 
     * @param columns The type of column can be Column or other clause
     * @param table table name string
     * @return
     */
    public AbstractFreeSqlBuilder from(String table) {
        return from(table(table));
    }
    
    /**
     * Append FROM and table for query. And if logic DB is sql server, it will 
     * append "WITH (NOLOCK)" by default 
     * 
     * @param columns The type of column can be Column or other clause
     * @param table table name clause
     * @return
     */
    public AbstractFreeSqlBuilder from(Table table) {
        return append(FROM).append(table).append(new SqlServerWithNoLock());
    }
    
    /**
     * Append WHERE alone with expressions.
     * 
     * Note: The String parameter will be wrapped by Expression.
     * 
     * @param expressions
     * @return
     */
    public AbstractFreeSqlBuilder where(Object...expressions) {
        return append(WHERE).appendExpressions(expressions);
    }
    
    /**
     * Append ORDER BY with column name
     * 
     * @param columnName
     * @param ascending
     * @return
     */
    public AbstractFreeSqlBuilder orderBy(String columnName, boolean ascending){
        return append(ORDER_BY).append(column(columnName)).appendWhen(ascending, ASC, DESC);
    }
    
    /**
     * Append GROUP BY with column name
     * 
     * @param columnName
     * @return
     */
    public AbstractFreeSqlBuilder groupBy(String columnName) {
        return append(GROUP_BY).append(column(columnName));
    }
    
    public AbstractFreeSqlBuilder groupBy(Clause condition) {
        return append(GROUP_BY).append(condition);
    }
    
    /**
     * Append HAVING with condition
     * 
     * @param condition
     * @return
     */
    public AbstractFreeSqlBuilder having(String condition) {
        return append(HAVING).append(condition);
    }
    
    public AbstractFreeSqlBuilder leftBracket() {
        return append(Expressions.leftBracket);
    }

    public AbstractFreeSqlBuilder rightBracket() {
        return append(Expressions.rightBracket);
    }
    
    /**
     * Append multiple expression into ().
     * 
     * Note: The String parameter will be wrapped by Expression.
     * 
     * @param expressions
     * @return
     */
    public AbstractFreeSqlBuilder bracket(Object... expressions) {
        return leftBracket().appendExpressions(expressions).rightBracket();
    }
    
    public AbstractFreeSqlBuilder and() {
        return append(Expressions.AND);
    }
    
    public AbstractFreeSqlBuilder or() {
        return append(Expressions.OR);
    }
    
    public AbstractFreeSqlBuilder not() {
        return append(Expressions.NOT);
    }
    
    /**
     * Join multiple expression with AND.
     * 
     * Note: The String parameter will be wrapped by Expression.
     * 
     * @param expressions
     * @return
     */
    public AbstractFreeSqlBuilder and(Object... expressions) {
        for (int i = 0; i < expressions.length; i++) {
            appendExpr(expressions[i]);
            if(i != expressions.length -1)
                and();    
        }
        
        return this;
    }

    /**
     * Join multiple expression with OR.
     * 
     * Note: The String parameter will be wrapped by Expression.
     * 
     * @param expressions
     * @return
     */
    public AbstractFreeSqlBuilder or(Object... expressions) {
        for (int i = 0; i < expressions.length; i++) {
            appendExpr(expressions[i]);
            if(i != expressions.length -1)
                or();    
        }
        
        return this;
    }
    
    private void appendExpr(Object expr) {
        if(expr instanceof String) {
            appendExpression((String)expr);
        }else {
            append(expr);
        }
    }
    
    /**
     * Set last expression in builder as nuallable and check against given value.
     * @param value
     * @return
     */
    public AbstractFreeSqlBuilder nullable(Object value) {
        List<Clause> list = clauses.list;
        
        if(list.isEmpty())
            throw new IllegalStateException("There is no exitsing sql segement.");
        
        Clause last = list.get(list.size() - 1);
        
        if(last instanceof Expression) {
            ((Expression)last).nullable(value);
            return this;
        }
        
        throw new IllegalStateException("The last sql segement is not an expression.");
    }

    /**
     * Append = expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder equal(String columnName) {
        return append(Expressions.equal(columnName));
    }
    
    /**
     * Append <> expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder notEqual(String columnName) {
        return append(Expressions.notEqual(columnName));
    }
    
    /**
     * Append > expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder greaterThan(String columnName) {
        return append(Expressions.greaterThan(columnName));
    }

    /**
     * Append >= expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder greaterThanEquals(String columnName) {
        return append(Expressions.greaterThanEquals(columnName));
    }

    /**
     * Append < expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder lessThan(String columnName) {
        return append(Expressions.lessThan(columnName));
    }

    /**
     * Append <= expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder lessThanEquals(String columnName) {
        return appendColumnExpression("%s <= ?", columnName);
    }

    /**
     * Append BETWEEN expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder between(String columnName) {
        return append(Expressions.between(columnName));
    }
    
    /**
     * Append LIKE expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder like(String columnName) {
        return append(Expressions.like(columnName));
    }
    
    /**
     * Append BOT LIKE expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder notLike(String columnName) {
        return append(Expressions.notLike(columnName));
    }
    
    /**
     * Append IN expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder in(String columnName) {
        return append(Expressions.in(columnName));
    }
    
    /**
     * Append NOT IN expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder notIn(String columnName) {
        return append(Expressions.notIn(columnName));
    }
    
    /**
     * Append IS NULL expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder isNull(String columnName) {
        return append(Expressions.isNull(columnName));
    }
    
    /**
     * Append IS NOT NULL expression using the giving name
     * @param columnName  column name, can not be expression.
     * @return
     */
    public AbstractFreeSqlBuilder isNotNull(String columnName) {
        return append(Expressions.isNotNull(columnName));
    }
    
    /**
     * Because certain infomation may not be in place during sql construction,
     * the build process is separated into two phases. One is preparing: setBuilderCOntext(), this 
     * is invoked immediately after clause is been constructed. The other is build(), which 
     * actually build part of the final sql.
     * 
     * @author jhhe
     *
     */
    public static abstract class Clause implements ClauseClassifier {
        /**
         * The build context will share the same context of the builder by default
         */
        protected BuilderContext context;
        
        public void setContext(BuilderContext context) {
            this.context = context;
        }
        
        public DatabaseCategory getDbCategory() {
            return context.getDbCategory();
        }

        public String getLogicDbName() {
            return context.getLogicDbName();
        }

        public DalHints getHints() {
            return context.getHints();
        }

        public StatementParameters getParameters() {
            return context.getParameters();
        }

        public boolean isExpression() {
            return false;
        }

        public boolean isNull() {
            return false;
        }

        public boolean isBracket() {
            return false;
        }

        public boolean isLeft() {
            return false;
        }

        public boolean isOperator() {
            return false;
        }
        
        public boolean isNot() {
            return false;
        }
        
        public abstract String build() throws SQLException;
    }
    
    public static class ClauseList extends Clause {
        private List<Clause> list = new LinkedList<>();
        
        public void setContext(BuilderContext context) {
            super.setContext(context);
            for(Clause c: list)
                c.setContext(context);
        }
        
        public ClauseList add(Clause... clauses) {
            for(Clause c: clauses) {
                c.setContext(context);
                list.add(c);
            }
            return this;
        }
        
        public boolean isEmpty() {
            return list.isEmpty();
        }

        @Override
        public String build() throws SQLException {
            StringBuilder sb = new StringBuilder();
            validate();
            for(Clause c: list)
                sb.append(c.build());
            return sb.toString();
        }
        
        private void validate() {
            
        }
    }
    
    public static class Text extends Clause {
        private String template;
        public Text(String template) {
            this.template =template;
        }
        
        public String build() {
            return template;
        }
        
        public String toString() {
            return build();
        }
    }
    
    public static class Column extends Clause {
        private String columnName;
        private String alias;
        public Column(String columnName) {
            this.columnName = columnName;
        }
        
        public Column(String columnName, String alias) {
            this(columnName);
            this.alias = alias;
        }
        
        public Column as(String alias) {
            this.alias = alias;
            return this;
        }
        
        public String build() {
            return alias == null ? wrapField(getDbCategory(), columnName): wrapField(getDbCategory(), columnName) + " AS " + alias;
        }
    }
    
    private AbstractFreeSqlBuilder appendColumnExpression(String template, String columnName) {
        return append(Expressions.createColumnExpression(template, columnName));
    }
    
    public static class Table extends Clause{
        private String rawTableName;
        private String alias;
        private String tableShardId;
        private Object tableShardValue;
        
        public Table(String rawTableName) {
            this.rawTableName = rawTableName;
        }
        
        public Table(String rawTableName, String alias) {
            this(rawTableName);
            this.alias = alias;
        }
        
        public Table inShard(String tableShardId) {
            this.tableShardId = tableShardId;
            return this;
        }
        
        public Table shardValue(String tableShardValue) {
            this.tableShardValue = tableShardValue;
            return this;
        }
        
        public Table as(String alias) {
            this.alias = alias;
            return this;
        }
        
        @Override
        public String build() throws SQLException {
            String logicDbName = getLogicDbName();
            DatabaseCategory dbCategory = getDbCategory();
            String tableName = null;

            if(!isTableShardingEnabled(logicDbName, rawTableName))
                tableName = wrapField(dbCategory, rawTableName);
            else if(tableShardId!= null)
                tableName = wrapField(dbCategory, rawTableName + buildShardStr(logicDbName, tableShardId));
            else if(tableShardValue != null) {
                tableName = wrapField(dbCategory, rawTableName + buildShardStr(logicDbName, locateTableShardId(logicDbName, rawTableName, new DalHints().setTableShardValue(tableShardValue), null, null)));
            }else
                tableName = wrapField(dbCategory, rawTableName + buildShardStr(logicDbName, locateTableShardId(logicDbName, rawTableName, getHints(), getParameters(), null)));
            
            return alias == null ? tableName : tableName + " AS " + alias;
        }
    }
    
    /**
     * Special Clause that only works when DB is sql server. It will append WITH (NOLOCK) after table
     * name against guideline.
     * @author jhhe
     *
     */
    private static class SqlServerWithNoLock extends Clause {
        private static final String SQL_SERVER_NOLOCK = "WITH (NOLOCK)";
        
        public String build() throws SQLException {
            return getDbCategory() == DatabaseCategory.SqlServer ? SQL_SERVER_NOLOCK : EMPTY;
        }
    }
    
    /**
     * Commonly used information for build the sql
     * 
     * @author jhhe
     */
    private class BuilderContext {
        private String logicDbName;
        private DatabaseCategory dbCategory;
        private DalHints hints;
        private StatementParameters parameters;
        
        public DatabaseCategory getDbCategory() {
            return dbCategory;
        }

        public String getLogicDbName() {
            return logicDbName;
        }

        public DalHints getHints() {
            return hints;
        }

        public StatementParameters getParameters() {
            return parameters;
        }

        public void setLogicDbName(String logicDbName) {
            Objects.requireNonNull(logicDbName, "logicDbName can not be NULL");
            // Check if exist
            this.dbCategory = DalClientFactory.getDalConfigure().getDatabaseSet(logicDbName).getDatabaseCategory(); 
            this.logicDbName = logicDbName;
        }
        
        public void setDbCategory(DatabaseCategory dbCategory) {
            Objects.requireNonNull(dbCategory, "dbCategory can not be NULL");
            if(logicDbName == null)
                this.dbCategory = dbCategory;
            else{
                if(this.dbCategory != dbCategory)
                    throw new IllegalArgumentException("The dbCategory does not match logic DB " + logicDbName);
            }
        }

        public void setHints(DalHints hints) {
            Objects.requireNonNull(hints, "DalHints can't be null.");
            this.hints = hints.clone();
        }
        
        public void setParameters(StatementParameters parameters) {
            Objects.requireNonNull(parameters, "parameters can't be null.");
            if(this.parameters == parameters)
                return;

            if(this.parameters != null && this.parameters.size() > 0)
                throw new IllegalStateException("The parameters has already be set and processed. " + 
                        "You can only set parameters at the begining of build");
                
            this.parameters = parameters;
        }
    }
    
    /**
     * If there is COMMA, then the leading space will not be appended.
     * If there is bracket, then both leading and trailing space will be omitted.
     * @param clauseList
     * @return
     * @throws SQLException
     */
    private String finalBuild(List<Clause> clauseList) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < clauseList.size(); i ++) {
            Clause curClause = clauseList.get(i);
            Clause nextClause = (i == clauseList.size() - 1) ? null: clauseList.get(i+1);
            
            sb.append(curClause.build());
            
            /**
             * Builder will not insert space before COMMA and bracket.
             */
            if(curClause.isBracket() || nextClause != null && (nextClause.isBracket() || nextClause == COMMA))
                continue;
            
            sb.append(SPACE);
        }
        
        return sb.toString().trim();
    }
}
