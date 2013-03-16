/*
 * Copyright (C) 2013 Reese Wilson | Shiny Mayhem

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.shinymayhem.radiopresets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.shinymayhem.radiopresets.DbContractRadio.DbHelperRadio;

public class ContentProviderRadio extends ContentProvider {

	protected ActivityLogger mLogger = new ActivityLogger();
	
	private static final String AUTHORITY = "com.shinymayhem.radiopresets.contentprovider";
	private static final int URI_STATIONS = 1;
	private static final int URI_STATION_ID = 2;
	private static final int URI_PRESET = 3;
	private static final int URI_PRESET_MAX = 4;
	
	//segments
	private static final String SEGMENT_STATIONS_BASE = "stations";
	private static final String SEGMENT_PRESETS_BASE = "presets";
	private static final String SEGMENT_PRESET_MAX = SEGMENT_PRESETS_BASE + "/max";
	
	//make each segment type available
	public static final Uri CONTENT_URI_STATIONS = Uri.parse("content://" + AUTHORITY + "/" + SEGMENT_STATIONS_BASE);
	public static final Uri CONTENT_URI_PRESETS = Uri.parse("content://" + AUTHORITY + "/" + SEGMENT_PRESETS_BASE);
	public static final Uri CONTENT_URI_PRESETS_MAX = Uri.parse("content://" + AUTHORITY + "/" + SEGMENT_PRESET_MAX);
	private DbHelperRadio mStationsHelper; 
	
	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static
	{
		sUriMatcher.addURI(AUTHORITY, SEGMENT_STATIONS_BASE, URI_STATIONS);
		sUriMatcher.addURI(AUTHORITY, SEGMENT_STATIONS_BASE+"/#", URI_STATION_ID);
		sUriMatcher.addURI(AUTHORITY, SEGMENT_PRESETS_BASE+"/#", URI_PRESET);
		sUriMatcher.addURI(AUTHORITY, SEGMENT_PRESET_MAX, URI_PRESET_MAX);
	}
	

	@Override
	public boolean onCreate() {
		mStationsHelper = new DbHelperRadio(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,	String sortOrder) {
		String groupBy = null;
		String having = null;
		String table = null;
		String limit = null;
		SQLiteDatabase db;
		switch (sUriMatcher.match(uri))
		{
		case URI_STATIONS: 
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getReadableDatabase();
			break;
		case URI_STATION_ID:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getReadableDatabase();
			long id = ContentUris.parseId(uri);
			selection = addColumn(DbContractRadio.EntryStation._ID, selection);
			selectionArgs = addArg(id, selectionArgs);
			break;
		case URI_PRESET:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getReadableDatabase();
			long presetNumber = ContentUris.parseId(uri);
			selection = addColumn(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER, selection);
			selectionArgs = addArg(presetNumber, selectionArgs);
			break;
		case URI_PRESET_MAX:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getReadableDatabase();
			//override any projection/selection arguments to get the max
			String[] newProjection = {"max(" + DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER + ") as " + DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER};
			//String newSelection = DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER + "= max(" + DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER + ")";
			selection = null;
			selectionArgs = null;
			projection = newProjection;
			break;
		default:
			throw new IllegalArgumentException("Unknown query URI:" + uri);
		}
		
		Cursor cursor = db.query(
				table, 
				projection, 
				selection, 
				selectionArgs, 
				groupBy,
				having,
				sortOrder, 
				limit
				//Integer.toString(ActivityMain.BUTTON_LIMIT)
			);
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		//log("query uri:" + uri, "v");
		return cursor;
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		
		String table = null;
		SQLiteDatabase db;
		boolean collapse = false;
		switch (sUriMatcher.match(uri))
		{
		case URI_STATIONS:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getWritableDatabase();
			collapse = true;
			break;
		case URI_STATION_ID:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getWritableDatabase();
			long id = ContentUris.parseId(uri);
			selection = addColumn(DbContractRadio.EntryStation._ID, selection);
			selectionArgs = addArg(id, selectionArgs);
			collapse = true;
			break;
		default:
			throw new IllegalArgumentException("Unknown delete URI:" + uri);
		}

		int deletedCount = db.delete(table, selection, selectionArgs);
		if (collapse)
		{
			this.collapsePresetNumbers();
		}
		//notify content resolver of data change
		getContext().getContentResolver().notifyChange(uri, null);
		log("delete uri:" + uri + ". " + String.valueOf(deletedCount) + " deleted", "v");
		return deletedCount;
		
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		String table = null;
		SQLiteDatabase db;
		switch (sUriMatcher.match(uri))
		{
		case URI_STATIONS:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			//if preset isset, make room, otherwise, append
			if (values.containsKey(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER))
			{
				makeRoomForPreset(values.getAsInteger(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER), 0);
			}
			else
			{
				int preset = this.getMaxPresetNumber() + 1;
				values.put(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER, preset);
			}
			db = mStationsHelper.getWritableDatabase();
			
			break;
		default:
			throw new IllegalArgumentException("Unknown insert URI:" + uri);
		}
		
		long id = db.insert(table, null, values);
		//notify content resolver of data change
		getContext().getContentResolver().notifyChange(uri, null);
		log("insert uri:" + uri + ". id of insert:" + String.valueOf(id), "v");
		return Uri.parse(SEGMENT_STATIONS_BASE + "/" + id);
		
		
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		String table = null;
		SQLiteDatabase db;
		switch (sUriMatcher.match(uri))
		{
		case URI_STATIONS:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getWritableDatabase();
			break;
		case URI_STATION_ID:
			table = DbContractRadio.EntryStation.TABLE_NAME;
			db = mStationsHelper.getWritableDatabase();
			long id = ContentUris.parseId(uri);
			selection = addColumn(DbContractRadio.EntryStation._ID, selection);
			selectionArgs = addArg(id, selectionArgs);
			if (values.containsKey(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER))
			{
				makeRoomForPreset(values.getAsInteger(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER), (int)id);
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown query URI:" + uri);
		}
		
		int updatedCount = db.update(table, values, selection, selectionArgs);
		//notify content resolver of data change
		getContext().getContentResolver().notifyChange(uri, null);
		log("update uri:" + uri + ". " + String.valueOf(updatedCount) + " updated", "v");
		return updatedCount;
		
	}
	
	//not used because not supported until api 11
	/*
	@Override 
	public Bundle call(String method, String arg, Bundle extras) 
	{
		Bundle values = new Bundle();
		if (method == "getMaxPresetNumber")
		{
			values.putInt(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER, this.getMaxPresetNumber());
			return values;
		}
		else
		{
			return super.call(method, arg, extras);
		}
		
	}*/
	
	private int getMaxPresetNumber()
	{
		Uri uri = CONTENT_URI_PRESETS_MAX;
		String[] projection = null;
		String selection = null;
		String[] selectionArgs = null;
		String sortOrder = null;
		Cursor cursor = this.query(uri, projection, selection, selectionArgs, sortOrder);
		long preset = 0;
		if (cursor.getCount() > 0)		
		{
			cursor.moveToFirst();
			preset = cursor.getLong(cursor.getColumnIndexOrThrow(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER));	
		}
		
		cursor.close();
		return (int)preset;
	}
	
	private void collapsePresetNumbers()
	{
		//SQLiteDatabase db = mStationsHelper.getReadableDatabase();	
		log("collapsePresetNumbers()", "v");
		Uri uri = CONTENT_URI_STATIONS;
		String[] projection = {DbContractRadio.EntryStation._ID, DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER};
		String selection = null;
		String[] selectionArgs = null;
		String sortOrder = DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER;
		Cursor cursor = this.query(uri, projection, selection, selectionArgs, sortOrder);
		if (cursor.getCount() > 0)
		{
			cursor.moveToFirst();
			for (int i = 0; i<cursor.getCount(); i++)
			{
				
				long id = cursor.getLong(cursor.getColumnIndexOrThrow(DbContractRadio.EntryStation._ID));
				long preset = cursor.getLong(cursor.getColumnIndexOrThrow(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER));
				uri = Uri.parse(CONTENT_URI_STATIONS.toString() + "/" + String.valueOf(id));
				ContentValues values = new ContentValues();
				values.put(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER, i+1);
				log("setting preset number for " + String.valueOf(id) + " from " + String.valueOf(preset) + " to " + String.valueOf(i+1), "v");
				this.update(uri, values, selection, selectionArgs);
				cursor.moveToNext();
			}
		}
		cursor.close();
		
	}
	/*
	private int fillInPreset(int preset)
	{
		//TODO change delete to individual to rearrange presets as they are deleted
		//alternatively, create a collapse function
		SQLiteDatabase db = mStationsHelper.getWritableDatabase();
		String column = DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER;
	
		//get list of stations that will be updates
		String[] newSelectionArgs = {String.valueOf(preset+1)};
		String sql = "select " + DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER +
				" from " + DbContractRadio.EntryStation.TABLE_NAME + 
				" where " + column + ">= ? ";
		Cursor newCursor = db.rawQuery(sql,	newSelectionArgs);
		
		int updatedCount = newCursor.getCount();
				//(int)newCursor.getLong(newCursor.getColumnIndexOrThrow(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER));
		newCursor.close();
		if (updatedCount > 0)
		{
			sql = "update " + DbContractRadio.EntryStation.TABLE_NAME + " " +
					" set " + column + " = " + column + " - 1 " +
					" where " + column + ">= " + String.valueOf(preset+1); //TODO sanitize
			db.execSQL(sql);
		}
		db.close();
		
		//return number of updated stations
		return updatedCount;
	}
	*/
	private int makeRoomForPreset(int preset, int id)
	{
		log("making room for preset:" + preset, "v");
		//check for existing entry with same preset but different id
		Uri uri = CONTENT_URI_STATIONS;
		String[] projection = {DbContractRadio.EntryStation._ID};
		String selection = DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER + " = ? and not " + DbContractRadio.EntryStation._ID + " = ? ";
		String[] selectionArgs = {String.valueOf(preset), String.valueOf(id)};
		String sortOrder = null;
		Cursor cursor = this.query(uri, projection, selection, selectionArgs, sortOrder);
		int updatedCount = 0;
		//if existing entry exists, increment all presets equal to or above current
		if (cursor.getCount() > 0)
		{
			log("preset exists, increment it and all above", "v");
			SQLiteDatabase db;
			db = mStationsHelper.getWritableDatabase();
			String column = DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER;
			/*db.beginTransaction();
			db.execSQL(sql);*/
			
			//get list of stations that will be updates
			String[] newSelectionArgs = {String.valueOf(preset)};
			String sql = "select " + DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER +
					" from " + DbContractRadio.EntryStation.TABLE_NAME + 
					" where " + column + ">= ? ";
			Cursor newCursor = db.rawQuery(sql,	newSelectionArgs);
			updatedCount = newCursor.getCount();
			log("incrementing " + String.valueOf(updatedCount) + " presets", "v");
					//(int)newCursor.getLong(newCursor.getColumnIndexOrThrow(DbContractRadio.EntryStation.COLUMN_NAME_PRESET_NUMBER));
			newCursor.close();
			if (updatedCount > 0)
			{
				sql = "update " + DbContractRadio.EntryStation.TABLE_NAME + " " +
						" set " + column + " = " + column + " + 1 " +
						" where " + column + ">= " + String.valueOf(preset); //TODO sanitize
				db.execSQL(sql);	
			}
			
			//db.rawQuery("select changes()", null);
			//db.endTransaction();
			//String[] newSelectionArgs = {String.valueOf(preset)};
			//Cursor newCursor = db.rawQuery(sql, newSelectionArgs);
			/**/
			/*
			ContentValues values = new ContentValues();
			values.put(column, column + " + 1 ");
			String whereClause = column + " >= ? ";
			String[] whereArgs = {String.valueOf(preset)}; 
			updatedCount = db.update(DbContractRadio.EntryStation.TABLE_NAME, values, whereClause, whereArgs);
			*/
			//newCursor.moveToFirst();
			
			//TODO find out if these are needed, or what contentprovider closes automatically and shouldn't be closed
			
			db.close();
			//return number of updated stations
			
		}
		else
		{
			log("preset doesn't exists yet, ok to add", "v");
		}
		cursor.close();
		return updatedCount;
	}
	
	
	//add id to selection string
	private String addColumn(String column, String selection)
	{
		if (selection == null || selection.isEmpty())
		{
			return column + " = ? ";	
		}
		return selection + " and " + column + " = ?";
	}
	
	//add id to selection args
	//TODO templatize if needed for other types
	private String[] addArg(long id, String[] selectionArgs)
	{
		
		List<String> args = new ArrayList<String>();
		if (selectionArgs != null)
		{
			Collections.addAll(args, selectionArgs);	
		}
		args.add(String.valueOf(id));
		//selectionArgs = (String[])(args.toArray());
		selectionArgs = args.toArray(new String[0]);
		return selectionArgs;
	}


	@Override
	public String getType(Uri uri) {
		//TODO check if this is right
		log("getType called", "e");
		throw new UnsupportedOperationException("getType not supported. if this is seen, return null instead?");
	}

	private void log(String text, String level)
	{
		mLogger.log(getContext(), "ContentProviderRadio", "ContentProviderRadio:\t\t"+text, level);
	}
	

}