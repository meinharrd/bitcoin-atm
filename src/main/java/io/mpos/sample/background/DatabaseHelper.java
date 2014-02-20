package io.mpos.sample.background;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.math.BigDecimal;

import io.mpos.sample.ui.MposApplication;
import io.mpos.transactions.DefaultTransaction;
import io.mpos.transactions.Transaction;
import io.mpos.transactions.TransactionType;

/**
 * This database helper eases the access to a table that us used to store all mock transaction. The
 * latest entry is used for the refund activity.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NAME = "dictionary";

    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_ID = "id";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    KEY_AMOUNT + " TEXT, " +
                    KEY_ID + " TEXT, " +
                    KEY_TIMESTAMP + " INTEGER);";

    private DatabaseHelper(Context context) {
        super(context, "LastTransactions", null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
        // won't happen
    }

    /**
     * Saves a transaction in the database
     */
    public static void saveInDatabase(Context context, Transaction transaction) {
        SQLiteOpenHelper helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        assert db != null;

        ContentValues values = new ContentValues();
        values.put(KEY_AMOUNT, transaction.getAmount().toString());
        values.put(KEY_ID, "" + transaction.getIdentifier());
        values.put(KEY_TIMESTAMP, transaction.getCreatedTimestamp());

        db.insert(TABLE_NAME, null, values);

        db.close();
        helper.close();
    }

    /**
     * Returns the transaction which has the largest timestamp value
     */
    public static Transaction getLastEntry(Context context) {
        SQLiteOpenHelper helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        assert db != null;

        // Get entry
        Cursor c = db.query(TABLE_NAME, new String[]{KEY_AMOUNT, KEY_ID, KEY_TIMESTAMP}, null, null, null, null, KEY_TIMESTAMP + " DESC");
        c.moveToFirst();

        if (c.isAfterLast())
            return null;

        // Parse entry
        BigDecimal amount = new BigDecimal(c.getString(0));
        String id = c.getString(1);
        long timestamp = c.getLong(2);

        DefaultTransaction transaction = new DefaultTransaction(amount, MposApplication.DEFAULT_CURRENCY, TransactionType.CHARGE);
        transaction.setCreatedTimestamp(timestamp);
        transaction.setIdentifier(id);

        c.close();
        db.close();
        helper.close();

        return transaction;
    }

    public static void deleteFromDatabase(Context context, String identifier) {
        SQLiteOpenHelper helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        assert db != null;

        db.delete(TABLE_NAME, KEY_ID + "=?", new String[]{identifier});

        db.close();
        helper.close();
    }
}
