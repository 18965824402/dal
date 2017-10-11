package com.ctrip.platform.dal.dao.sqlbuilder;

import static com.ctrip.platform.dal.dao.sqlbuilder.AbstractTableSqlBuilder.wrapField;

import java.util.Objects;

/**
 * A factory of static expression methods.
 * 
 * @author jhhe
 *
 */
public class Expressions {
    public static final NullClause NULL = new NullClause();
    
    public static final Operator AND = new Operator("AND");
    
    public static final Operator OR = new Operator("OR");
    
    public static final Operator NOT = new Operator("NOT");
    
    public static final Bracket leftBracket = new Bracket(true);

    public static final Bracket rightBracket = new Bracket(false);
    
    public static Expression createColumnExpression(String template, String columnName) {
        return new ColumnExpression(template, columnName);
    }
    
    /**
     * Create Expression clause with the given template
     * @param template
     * @return
     */
    public static Expression expression(String template) {
        return new Expression(template);
    }

    public static Expression expression(boolean condition, String template) {
        return condition ? new Expression(template) : NULL;
    }
    
    public static Expression expression(boolean condition, String template, String elseTemplate) {
        return condition ? expression(template) : expression(elseTemplate);
    }
    
    public static Clause bracket(Clause... clauses) {
        ClauseList list = new ClauseList();
        return list.add(leftBracket).add(clauses).add(rightBracket);
    }

    public static Expression equal(String columnName) {
        return createColumnExpression("%s = ?", columnName);
    }
    
    public static Expression notEqual(String columnName) {
        return createColumnExpression("%s <> ?", columnName);
    }
    
    public static Expression greaterThan(String columnName) {
        return createColumnExpression("%s > ?", columnName);
    }

    public static Expression greaterThanEquals(String columnName) {
        return createColumnExpression("%s >= ?", columnName);
    }

    public static Expression lessThan(String columnName) {
        return createColumnExpression("%s < ?", columnName);
    }

    public static Expression lessThanEquals(String columnName) {
        return createColumnExpression("%s <= ?", columnName);
    }

    public static Expression between(String columnName) {
        return createColumnExpression("%s BETWEEN ? AND ?", columnName);
    }
    
    public static Expression like(String columnName) {
        return createColumnExpression("%s LIKE ?", columnName);
    }
    
    public static Expression notLike(String columnName) {
        return createColumnExpression("%s NOT LIKE ?", columnName);
    }
    
    public static Expression in(String columnName) {
        return createColumnExpression("%s IN(?)", columnName);
    }
    
    public static Expression notIn(String columnName) {
        return createColumnExpression("%s NOT IN(?)", columnName);
    }
    
    public static Expression isNull(String columnName) {
        return createColumnExpression("%s IS NULL", columnName);
    }
    
    public static Expression isNotNull(String columnName) {
        return createColumnExpression("%s IS NOT NULL", columnName);
    }
    
    public static class Operator extends Clause {
        private String operator;
        public Operator(String operator) {
            this.operator = operator;
        }
        
        @Override
        public String build() {
            return operator;
        }
        
        public boolean isOperator() {
            return true;
        }
        
        public boolean isNot() {
            return this == NOT;
        }
    }
    
    public static class Bracket extends Clause {
        private boolean left;
        public Bracket(boolean isLeft) {
            left = isLeft;
        }

        public String build() {
            return left? "(" : ")";
        }
        
        public boolean isBracket() {
            return true;
        }

        public boolean isLeft() {
            return left;
        }
    }
    
    public static class Expression extends Clause {
        private String template;
        private boolean nullValue = false;
        
        public Expression(String template) {
            this.template = template;
        }
        
        public Expression nullable(Object o) {
            nullValue = (o == null);
            return this;
        }

        public boolean isExpression() {
            return true;
        }
        
        public boolean isNull() {
            return nullValue;
        }
        
        public String build() {
            if(nullValue)
                throw new IllegalStateException("Null expression should not be removed instead of build");
            
            return template;
        }
    }
    
    public static class ColumnExpression extends Expression {
        private String columnName;
        
        public ColumnExpression(String template, String columnName) {
            super(template);
            Objects.requireNonNull(columnName, "column name can not be null");
            this.columnName = columnName;
        }
        
        public String build() {
            String template = super.build();
            return columnName == null ? template : String.format(template, wrapField(getDbCategory(), columnName));
        }
    }
    
    /**
     * This clause is just a placeholder that can be removed from the expression clause list.
     * @author jhhe
     *
     */
    public static class NullClause extends Expression {
        public NullClause() {
            super("");
        }
        
        public boolean isNull() {
            return true;
        }
        
        @Override
        public String build() {
            return "";
        }
    }
}
