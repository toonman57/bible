/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2012 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.app.Activity;
import android.app.SearchManager;
import android.os.Bundle;

import android.view.View;
import android.view.View.OnKeyListener;

import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;

import android.content.Intent;

import android.net.Uri;
import android.util.Log;
import android.database.Cursor;

import android.preference.PreferenceManager;

public class Search extends Activity
{
    private TextView textView = null;
    private ListView listView = null;;

    private String version = null;
    private String query = null;

    private SimpleCursorAdapter adapter = null;

    private boolean refreshed = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        textView = (TextView) findViewById(R.id.text);
        listView = (ListView) findViewById(R.id.list);

        // TODO: support choose version, chapters, ...
        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            setVersion();
            query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        }

        if (version == null) {
            textView.setText(R.string.noversion);
        }

    }

    private boolean doSearch(String query) {
        if (version == null) {
            return false;
        }

        Provider.setVersions();
        Log.d(Provider.TAG, "search \"" + query + "\" in version \"" + version + "\"");
        if (Provider.versions.indexOf("rcuvss") >= 0 && Provider.isCJK(query.charAt(0)) && !Provider.isCJKVersion(version)) {
            Log.d(Provider.TAG, "\"" + version + "\" is not a cjk version, change to rcuvss");
            version = "rcuvss";
        } else if (Provider.versions.indexOf("niv") >= 0 && !Provider.isCJK(query.charAt(0)) && Provider.isCJKVersion(version)) {
            Log.d(Provider.TAG, "\"" + version + "\" is a cjk version, change to niv");
            version = "niv";
        }

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendEncodedPath(query).fragment(version).build();
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {query, version}));
            return false;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results,
                    count, new Object[] {count, query, version});
            textView.setText(countString);
        }
        showResults(cursor);
        return true;
    }

    private void closeAdapter() {
        if (adapter != null) {
            Cursor cursor = adapter.getCursor();
            cursor.close();
            adapter = null;
        }
    }

    private void showResults(Cursor cursor) {

        String[] from = new String[] {
            Provider.COLUMN_HUMAN,
            Provider.COLUMN_VERSE,
            Provider.COLUMN_UNFORMATTED,
        };

        int[] to = new int[] {
            R.id.human,
            R.id.verse,
            R.id.unformatted,
        };

        closeAdapter();
        adapter = new SimpleCursorAdapter(this,
            R.layout.result, cursor, from, to);
        adapter.setViewBinder(new ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                int verseIndex = cursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                if (columnIndex == verseIndex) {
                    int[] chapterVerse = Provider.getChapterVerse(cursor.getString(verseIndex));
                    String string = getString(R.string.search_result_verse,
                        new Object[] {chapterVerse[0], chapterVerse[1]});
                    TextView textView = (TextView) view;
                    textView.setText(string);
                    return true;
                }

                if (columnIndex == cursor.getColumnIndexOrThrow(Provider.COLUMN_UNFORMATTED)) {
                    String context = cursor.getString(columnIndex);
                    context = context.replaceAll("「", "“").replaceAll("」", "”");
                    context = context.replaceAll("『", "‘").replaceAll("』", "’");
                    ((TextView)view).setText(context);
                    return true;
                }
                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showVerse(String.valueOf(id));
            }
        });
    }

    private boolean showVerse(String id) {
        if (id == null) {
            return false;
        }
        Uri uri = Provider.CONTENT_URI_VERSE.buildUpon().appendEncodedPath(id).fragment(version).build();
        Cursor verseCursor = getContentResolver().query(uri, null, null, null, null);

        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = Provider.getChapterVerse(verse);
        String osis = book + "." + chapterVerse[0];
        Log.d(Provider.TAG, "show osis: " + osis + ", version: " + version);
        verseCursor.close();

        Intent chapterIntent = new Intent(getApplicationContext(), Chapter.class);
        Uri data = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).fragment(version).build();
        chapterIntent.setData(data);
        chapterIntent.putExtra("verse", chapterVerse[1]);
        startActivity(chapterIntent);

        return true;
    }

    private void setVersion() {
        Provider.setVersions();
        version = Provider.databaseVersion;
        if (version.equals("")) {
            version = PreferenceManager.getDefaultSharedPreferences(this).getString("version", null);
            if (version != null && Provider.versions.indexOf(version) < 0) {
                version = null;
            }
            if (version == null && Provider.versions.size() > 0) {
                version = Provider.versions.get(0);
            }
        }

        Log.d(Provider.TAG, "set version: " + version);
    }

    @Override
    public void onResume() {
        refreshed = true;
        super.onResume();
    }
}
