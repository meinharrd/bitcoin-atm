package io.mpos.sample.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.EnumSet;

import io.mpos.Mpos;
import io.mpos.MposExtended;
import io.mpos.accessories.Accessory;
import io.mpos.accessories.AccessoryUpdateRequirementComponent;
import io.mpos.errors.MposError;
import io.mpos.helper.Log;
import io.mpos.paymentdetails.PaymentDetailsScheme;
import io.mpos.provider.ProviderMode;
import io.mpos.provider.ProviderOptions;
import io.mpos.provider.ProviderOptionsFactory;
import io.mpos.provider.ProviderWithServerSubsystem;
import io.mpos.provider.listener.AccessoryConnectionStateListener;
import io.mpos.sample.R;
import io.mpos.transactions.Transaction;
import io.mpos.transactions.TransactionAction;
import io.mpos.transactions.actionresponse.TransactionActionResponse;
import io.mpos.transactions.actionresponse.TransactionActionResponseFactory;

/**
 * This super class provides convenient methods and implementations for the actual activities.
 * <p/>
 * It is responsible for setting the provider reference (<code>mProvider</code>) and it also
 * can display sample dialogs for possible interactions with the user (e.g. signature request).
 * <p/>
 * All sub classes must set the <code>mTextViewAccessoryState</code> and
 * <code>mProgressBar</code> as this UI components are referenced by helper methods.
 */
abstract class MposActivity extends Activity implements AccessoryConnectionStateListener {

    TextView mTextViewAccessoryState;
    ProgressBar mProgressBar;

    /**
     * Adjust the following constants to your needs. If you don't have received any credentials
     * yet, contact our sales team!
     */
    private final static String MERCHANT_IDENTIFIER = "10d1cf2e-10dc-4bd2-8b2c-e234ce556a1b";
    private final static String MERCHANT_SECRET_KEY = "QAi5Y6nbhymbPqpEzfWJyzNtYsAYnFAb";

    /**
     * We make testing for you very easy: If you are running the app on the emulator,
     * everything is setup in MOCK mode.
     * </p>
     * Running the app on real device, the connected Miura shuttle and you server
     * will be used for transaction processing.
     */
    protected final boolean MIURA_SHUTTLE_AVAILABLE = !MposApplication.isRunningOnEmulator();


    ProviderWithServerSubsystem mProvider;

    /**
     * Keeps track of the current accessory, so we can disconnect after a transaction ended
     * (successfully or not).
     */
    static Accessory currentAccessory;

    /**
     * Configuration of the payment details scheme as used for charge and refund transactions
     */
    final static PaymentDetailsScheme[] PAYMENT_SCHEMES = new PaymentDetailsScheme[]{
            PaymentDetailsScheme.ELV, PaymentDetailsScheme.MASTERCARD, PaymentDetailsScheme.VISA
    };


    private final static String LOG_TAG = MposActivity.class.getSimpleName();
    private AlertDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Modify mock configuration
        if(!MIURA_SHUTTLE_AVAILABLE) {
            MposExtended.getMockConfiguration().setDelayLong(0.8);
            MposExtended.getMockConfiguration().setDelayShort(0.4);
            MposExtended.getMockConfiguration().setUpdateRequirementComponents(EnumSet.of(AccessoryUpdateRequirementComponent.SECURITY));
        }

        ProviderMode providerMode = ProviderMode.MOCK_FLAT;
        if(MIURA_SHUTTLE_AVAILABLE) {
            providerMode = ProviderMode.TEST;
        }

        // Specify provider
        ProviderOptionsFactory providerOptionsFactory = Mpos.getProviderOptionsFactory();
        ProviderOptions providerOptions = providerOptionsFactory.createProviderOptions(
                providerMode,
                MERCHANT_IDENTIFIER,
                MERCHANT_SECRET_KEY,
                EnumSet.of(TransactionAction.CUSTOMER_IDENTIFICATION, TransactionAction.CUSTOMER_SIGNATURE)
        );

        // Create provider (or get the current one if existing).
        // Note: This cast is always possible. However, the actual implementation might throw an
        // exception when the additional methods are not implemented or do not make sense.
        mProvider = (ProviderWithServerSubsystem) Mpos.getOrCreateSharedProvider(this.getApplicationContext(), providerOptions);
    }

    @Override
    public void onStart() {
        super.onStart();
        mProvider.addAccessoryConnectionStateListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mProvider.removeAccessoryConnectionStateListener(this);
    }

    @Override
    public void onAccessoryConnectSuccess(Accessory accessory) {
        currentAccessory = accessory;

        setAccessoryStateText("Connected");
    }

    @Override
    public void onAccessoryConnectFailure(MposError mposError) {
        setOngoingTransaction(false);
        currentAccessory = null;

        setAccessoryStateText("Failure during connect");
    }

    @Override
    public void onAccessoryDisconnectSuccess(Accessory accessory) {
        setOngoingTransaction(false);
        currentAccessory = null;

        setAccessoryStateText("Disconnected");
    }

    @Override
    public void onAccessoryDisconnectFailure(Accessory accessory, MposError mposError) {
        setOngoingTransaction(false);
        currentAccessory = null;

        setAccessoryStateText("Failure during disconnect");
    }

    //
    // Dialog methods
    //

    void createActionRequiredDialog(final Transaction transaction, final TransactionAction transactionAction) {
        // we will handle all actions by generating alert dialogs
        switch (transactionAction) {
            case CUSTOMER_IDENTIFICATION:
                createActionCustomerIdentificationDialog(transaction, transactionAction);
                break;
            case CUSTOMER_SIGNATURE:
                createActionCustomerSignatureDialog(transaction, transactionAction);
                break;
            default:
                // If we cannot handle this action, we will abort
                mProvider.abortTransaction(transaction);
                break;
        }
    }

    void createActionCustomerSignatureDialog(final Transaction transaction, final TransactionAction transactionAction) {

        // Build a custom view that also display our default signature
        View view = getLayoutInflater().inflate(R.layout.dialog_signature, null);
        assert view != null;
        TextView tv = (TextView) view.findViewById(R.id.dialog_textView);
        if(transactionAction.equals(TransactionAction.CUSTOMER_SIGNATURE)) {
            tv.setText("Please sign in the field below:");
        } else {
            tv.setText("The transaction can be continued when you have handled:\n\n" + transactionAction.toString());
        }

        // Build alert dialog
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("An action is required");
        builder.setView(view);
        builder.setCancelable(false);
        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                View v = mDialog.findViewById(R.id.dialog_imageView);
                v.buildDrawingCache();
                Bitmap b1 = v.getDrawingCache();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                b1.compress(Bitmap.CompressFormat.PNG, 100, out);
                v.destroyDrawingCache();

                // Create appropriate response
                TransactionActionResponseFactory transactionActionResponseFactory =
                        mProvider.getTransactionActionResponseFactory();
                TransactionActionResponse actionResponse =
                        transactionActionResponseFactory.createResponseForSignatureAction(out.toByteArray(), true);
                mProvider.continueTransaction(transaction, transactionAction, actionResponse);
            }
        });

        builder.setNegativeButton("Abort", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mProvider.abortTransaction(transaction);
            }
        });

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDialog = builder.create();
                mDialog.show();
            }
        });
    }

    void createActionCustomerIdentificationDialog(final Transaction transaction, final TransactionAction transactionAction) {
        // Normally, you would ask the merchant to verify the identity of the
        // cardholder.
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("An action is required");
        builder.setMessage("The transaction can be continued when you have handled:\n\n" + transactionAction.toString());
        builder.setCancelable(false);
        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                TransactionActionResponseFactory transactionActionResponseFactory =
                        mProvider.getTransactionActionResponseFactory();
                mProvider.continueTransaction(
                        transaction,
                        transactionAction,
                        transactionActionResponseFactory.createResponseForIdentificationAction(true));
            }
        });
        builder.setNegativeButton("Abort", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mProvider.abortTransaction(transaction);
            }
        });
        builder.create().show();
    }

    //
    // User interface methods
    //

    void updateProgress(final boolean showThrobber, final String currentMessage) {
        Log.d("ChargeActivity", currentMessage);
        mProgressBar.setVisibility(showThrobber ? View.VISIBLE : View.INVISIBLE);
        if (currentMessage != null) {
            //noinspection ConstantConditions
            final Toast t = Toast.makeText(getApplicationContext(), currentMessage, Toast.LENGTH_SHORT);
            t.show();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    t.cancel();
                }
            }, 750);
        }
    }

    void setAccessoryStateText(final String stateText) {
        mTextViewAccessoryState.setText(stateText);
    }

    //
    // Handling the activity state (e.g. if it gets recreated)
    //

    /**
     * Is true iff we are having an ongoing transaction. This extra state variable is necessary
     * to consistently update the UI after a recreation (due to screen rotation or similar).
     */
    private boolean mOngoingTransaction = false;

    void setOngoingTransaction(final boolean thereIsAOngoingTransaction) {
        mOngoingTransaction = thereIsAOngoingTransaction;
        mProgressBar.setVisibility(thereIsAOngoingTransaction ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("mOngoingTransaction", mOngoingTransaction);
        //noinspection ConstantConditions
        outState.putString("mTextViewAccessoryState.text", mTextViewAccessoryState.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setOngoingTransaction(savedInstanceState.getBoolean("mOngoingTransaction"));
        setAccessoryStateText(savedInstanceState.getString("mTextViewAccessoryState.text"));
    }
}
