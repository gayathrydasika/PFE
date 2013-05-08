package com.tunav.tunavmedi.demo.sqlite;

import com.tunav.tunavmedi.demo.sqlite.contract.CredentialsContract;
import com.tunav.tunavmedi.demo.sqlite.contract.TasksContract;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


public class DemoSQLite extends SQLiteOpenHelper {

	private static final String tag = "DemoSQLite";

	private static final String DATABASE_NAME = "helpers";
	private static final int DATABASE_VERSION = 1;

	public DemoSQLite(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		Log.v(tag, "DemoSQLite()");
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.v(tag, "onCreate()");
		Log.i(tag, "Creating New database.");
		try {
			// Credentials
			Log.v(tag, CredentialsContract.SQL_CREATE_TABLE);
			db.execSQL(CredentialsContract.SQL_CREATE_TABLE);
			Log.v(tag, CredentialsContract.SQL_CREATE_INDEX_LOGIN);
			db.execSQL(CredentialsContract.SQL_CREATE_INDEX_LOGIN);
			db.execSQL(CredentialsContract.SQL_INSERT_DUMMY);
			Log.v(tag, CredentialsContract.SQL_INSERT_DUMMY);

			// Tasks
			Log.v(tag, TasksContract.SQL_CREATE_TABLE);
			db.execSQL(TasksContract.SQL_CREATE_TABLE);
			Log.v(tag, TasksContract.SQL_CREATE_INDEX_ID);
			db.execSQL(TasksContract.SQL_CREATE_INDEX_ID);
			Log.v(tag, TasksContract.SQL_INSERT_DUMMY);
			db.execSQL(TasksContract.SQL_INSERT_DUMMY);
		} catch (SQLException tr) {
			Log.e(tag, "SQLException");
			Log.d(tag, "SQLException", tr);
		}
		Log.i(tag, "New database created.");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(tag, "Version mismatch, upgrading...");
		try {
			Log.v(tag, CredentialsContract.SQL_DROP_INDEX_LOGIN);
			db.execSQL(CredentialsContract.SQL_DROP_INDEX_LOGIN);
			Log.v(tag, CredentialsContract.SQL_DROP_TABLE);
			db.execSQL(CredentialsContract.SQL_DROP_TABLE);
			Log.v(tag, TasksContract.SQL_DROP_INDEX_ID);
			db.execSQL(TasksContract.SQL_DROP_INDEX_ID);
			Log.v(tag, TasksContract.SQL_DROP_TABLE);
			db.execSQL(TasksContract.SQL_DROP_TABLE);
			onCreate(db);
		} catch (SQLException e) {
			Log.e(tag, "SQLException");
			Log.d(tag, "SQLException", e);
		}
	}

	@Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
			throws SQLException {
		// TODO dummy implementation
		Log.v(tag, "onDowngrade()");
		Log.i(tag, "Version mismatch, downgrading...");
		onUpgrade(db, oldVersion, newVersion);
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		Log.v(tag, "onOpen()");
	}
}
