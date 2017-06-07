package com.ctrip.platform.dal.dao.task;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ctrip.platform.dal.dao.DalHints;
import com.ctrip.platform.dal.dao.StatementParameters;

public class SingleInsertTask<T> extends InsertTaskAdapter<T> implements SingleTask<T> {
	
	@Override
	public int execute(DalHints hints, Map<String, ?> fields, T rawPojo) throws SQLException {
		List<Map<String, ?>> pojoList = new ArrayList<Map<String,?>>();
		List<T> rawPojos = new ArrayList<>();
		
		pojoList.add(fields);
		rawPojos.add(rawPojo);
		
		Set<String> unqualifiedColumns = filterUnqualifiedColumns(hints, pojoList, rawPojos);
		
		removeUnqualifiedColumns(fields, unqualifiedColumns);

//		if(fields.size() == 0)
//			return 0;

		String insertSql = buildInsertSql(hints, fields);
		
		StatementParameters parameters = new StatementParameters();
		addParameters(parameters, fields);
		
		return client.update(insertSql, parameters, hints);
	}
	
	private String buildInsertSql(DalHints hints, Map<String, ?> fields) throws SQLException {
		Set<String> remainedColumns = fields.keySet();
		String cloumns = combineColumns(remainedColumns, COLUMN_SEPARATOR);
		String values = combine(PLACE_HOLDER, remainedColumns.size(), COLUMN_SEPARATOR);

		return String.format(TMPL_SQL_INSERT, getTableName(hints, fields), cloumns, values);
	}
}
