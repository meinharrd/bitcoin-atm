package io.mpos.sample.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.mpos.MposExtended;
import io.mpos.accessories.Accessory;
import io.mpos.accessories.AccessoryOptions;
import io.mpos.accessories.AccessoryOptionsFactory;
import io.mpos.accessories.AccessoryType;
import io.mpos.accessories.AccessoryUpdateRequirement;
import io.mpos.accessories.AccessoryUpdateRequirementStatus;
import io.mpos.errors.MposError;
import io.mpos.mock.MockConfiguration;
import io.mpos.provider.listener.AccessoryUpdateListener;
import io.mpos.provider.listener.TransactionListener;
import io.mpos.provider.listener.TransactionRegisterListener;
import io.mpos.sample.R;
import io.mpos.sample.background.DatabaseHelper;
import io.mpos.transactions.Transaction;
import io.mpos.transactions.TransactionAction;
import io.mpos.transactions.TransactionTemplate;
import io.mpos.transactions.TransactionTemplateFactory;
import io.mpos.transactions.TransactionType;
import io.mpos.transactions.actionsupport.TransactionActionSupport;

/**
 * A sample fragment for creating and initiating new charge transactions.
 */
public class ChargeActivity extends MposActivity implements TransactionRegisterListener, AccessoryUpdateListener, TransactionListener {
    private EditText mEditTextAmount;
    private Button buttonStart;

    private static final int REQUEST_CODE = 0;

    /**
     * Here we temporarily store the transaction we handle. This is necessary as the intermediate
     * update workflow does not keep track of the transaction object.
     */
    private static final Queue<Transaction> pendingTransactions = new ConcurrentLinkedQueue<Transaction>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_charge);

        mTextViewAccessoryState = (TextView) findViewById(R.id.accessory_state);
        mEditTextAmount = (EditText) findViewById(R.id.charge_edit_amount);
        mProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        buttonStart = (Button) findViewById(R.id.button_start);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickedButtonStart();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if(!MIURA_SHUTTLE_AVAILABLE) {
            // Configure mock behaviour
            MposExtended.getMockConfiguration().setProcessingBehaviour(MockConfiguration.ProcessingBehaviour.APPROVE);
            MposExtended.getMockConfiguration().setPaymentBehaviour(MockConfiguration.PaymentBehaviour.APPROVE_WITH_SIGNATURE);
        }

        // register us as a listener
        mProvider.addAccessoryUpdateListener(this);
        mProvider.addTransactionRegisterListener(this);
        mProvider.addTransactionListener(this);
        // connection state listener is already registered my the MposActivity
    }

    @Override
    public void onPause() {
        super.onPause();
        // remove us as a listener
        mProvider.removeAccessoryUpdateListener(this);
        mProvider.removeTransactionRegisterListener(this);
        mProvider.removeTransactionListener(this);
    }

    private void onClickedButtonStart() {
        setOngoingTransaction(true);

        // get details
        assert mEditTextAmount != null;
        assert mEditTextAmount.getText() != null;
        String stringAmount = mEditTextAmount.getText().toString();

        // empty amount field
        if (stringAmount.equals("")) {
            setOngoingTransaction(false);
            return;
        }

        BigDecimal amountEuro = new BigDecimal(stringAmount).multiply(new BigDecimal(BitcoinIntegration.EXCHANGE_RATE_BTC_EUR));

        // EUR amount too small
        if (amountEuro.compareTo(new BigDecimal("0.01")) < 1) {
            setOngoingTransaction(false);
            return;
        }

        // Normally you would initialize a transaction from your server environment and share the
        // reference with the SDK in order to process it:
        //
        //    mProvider.lookupTransactionWithIdentifier("the_identifier_string_from_your_backend");
        //
        // We also offer a way to do this on the mobile site, but we encourage everyone to use this
        // option for prototyping/testing purposes only. This requires you to manually cast to the
        // provider 'ProviderWithServerSubsystem'.

        // specify transaction
            TransactionTemplateFactory transactionTemplateFactory = mProvider.getTransactionTemplateFactory();
            TransactionTemplate template = transactionTemplateFactory.
                    createTemplateWithAmount(amountEuro, MposApplication.DEFAULT_CURRENCY, TransactionType.CHARGE, "subject", "custom identifier");

            // register transaction
            mProvider.registerTransactionWithTemplate(template);
        //}


        // the onTransactionRegisterSuccess() method will then start the transaction

        updateProgress(true, "Registering transaction...");
    }

    // First, the registration succeeds:

    @Override
    public void onTransactionRegisterSuccess(Transaction transaction) {
        if (transaction.getType() != TransactionType.CHARGE)
            return;

        // First, we have to keep track that we want to process this transaction
        pendingTransactions.add(transaction);

        // Specify our connection / the accessory
        AccessoryOptionsFactory accessoryOptionsFactory = mProvider.getAccessoryOptionsFactory();
       AccessoryOptions accessoryOptions = accessoryOptionsFactory.createBluetoothOptions(AccessoryType.MIURA_SHUTTLE, "bluetooth" , "");

        // Connect to accessory
        setAccessoryStateText("Connecting...");
        mProvider.connectToAccessory(accessoryOptions);
    }

    // Second, we check if an update is required

    @Override
    public void onAccessoryConnectSuccess(Accessory accessory) {
        super.onAccessoryConnectSuccess(accessory);
        // check if update is required
        mProvider.checkUpdateRequirementForAccessory(accessory);
    }

    // Third, the update check succeeds:

    @Override
    public void onAccessoryCheckUpdateSuccess(Accessory accessory, AccessoryUpdateRequirement updateRequirement) {
        AccessoryUpdateRequirementStatus accessoryUpdateRequirementStatus = updateRequirement.getUpdateRequirementStatus();
        switch (accessoryUpdateRequirementStatus) {
            case NO_UPDATE_AVAILABLE:
                // we can go straight forward
                this.onAccessoryUpdateSuccess(accessory);
                return;

            case UPDATE_AVAILABLE_BUT_IN_GRACE_PERIOD:
            case UPDATE_AVAILABLE_AND_REQUIRED:
                updateProgress(true, "Updating...");
                setAccessoryStateText("Updating...");
                // we must (or at least we should) update
                mProvider.updateAccessory(accessory);
        }
    }

    @Override
    public void onAccessoryCheckUpdateFailure(Accessory accessory, MposError mposError) {
        setOngoingTransaction(false);
        updateProgress(false, "Checking for updates failed!");

        // disconnect from accessory
        tryToDisconnect();
    }

    // Fourth, the update itself succeeds (or is skipped):

    @Override
    public void onAccessoryUpdateSuccess(Accessory accessory) {
        updateProgress(true, "Update process finished.");
        setAccessoryStateText("Connected.");
        // now we can proceed :)
        Transaction transaction = pendingTransactions.poll();
        if (transaction == null)
            return;
        mProvider.startTransaction(transaction, PAYMENT_SCHEMES, accessory);
    }

    // Perhaps, we have to react on user action:

    @Override
    public void onTransactionActionRequired(Transaction transaction, TransactionAction transactionAction, TransactionActionSupport transactionActionSupport) {
        updateProgress(true, "Transaction requires action: " + transactionAction);

        // Handle it! (here implemented by the MposActivity super class)
        createActionRequiredDialog(transaction, transactionAction);
    }

    // Call Bitcoin wallet app

    public void transferBitcoins() {
        // get details
        assert mEditTextAmount != null;
        assert mEditTextAmount.getText() != null;
        String stringAmount = mEditTextAmount.getText().toString();
        BigDecimal amount = new BigDecimal(stringAmount);
        long amountNanocoins = amount.multiply(new BigDecimal(BitcoinIntegration.NANOCOINS_PER_COIN)).intValue();

        BitcoinIntegration.requestForResult(ChargeActivity.this, REQUEST_CODE, "n1z13CoY5ssrCKDMZzU1wetgvxchao1wiu", amountNanocoins);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        if (requestCode == REQUEST_CODE)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                Intent intent = new Intent(getApplicationContext(), BitcoinOkActivity.class);
                startActivity(intent);
            }
            else if (resultCode == Activity.RESULT_CANCELED)
            {
                Intent intent = new Intent(getApplicationContext(), BitcoinCanceledActivity.class);
                startActivity(intent);
            }
            else
            {
                Intent intent = new Intent(getApplicationContext(), BitcoinFailedActivity.class);
                startActivity(intent);
            }
        }
    }

    // Finally, the transaction succeeds:

    @Override
    public void onTransactionSuccess(Transaction transaction) {
        setOngoingTransaction(false);
        updateProgress(false, "Transaction successful!");

        // store transaction for later refunds
        if (transaction != null && transaction.getType() == TransactionType.CHARGE)
            DatabaseHelper.saveInDatabase(this, transaction);

        transferBitcoins();

        // disconnect from accessory
        tryToDisconnect();
    }

    //
    // Remaining listener methods
    //

    @Override
    public void onTransactionLookupSuccess(Transaction transaction) {
        // ignored here as we use the register transaction concept.
        // However, in a real world application this would contain almost the same code as in
        // the onTransactionRegisterSuccess() method.
    }

    @Override
    public void onTransactionLookupFailure(MposError mposError) {
        // ignored here as we use the register transaction concept.
        // However, in a real world application this would contain almost the same code as in
        // the onTransactionRegisterFailure() method.
    }

    @Override
    public void onTransactionFailure(Transaction transaction, MposError mposError) {
        setOngoingTransaction(false);
        if (mposError != null)
            updateProgress(false, "Transaction failed: " + mposError.getInfo());
        else
            updateProgress(false, "Transaction processed");

        // presentation hack - if payment fails
        transferBitcoins();

        tryToDisconnect();
    }

    @Override
    public void onTransactionRegisterFailure(MposError mposError) {
        setOngoingTransaction(false);
        updateProgress(false, "Failed to register transaction.");
    }

    @Override
    public void onAccessoryUpdateFailure(Accessory accessory, MposError mposError) {
        setOngoingTransaction(false);
        updateProgress(false, "Updating failed!");

        // disconnect from accessory
        tryToDisconnect();
    }



    private void tryToDisconnect() {
        // disconnect from accessory
        if (currentAccessory != null) {
            setAccessoryStateText("Disconnecting...");
            mProvider.disconnectFromAccessory(currentAccessory);
            currentAccessory = null;
        }
    }

    void setOngoingTransaction(final boolean thereIsAOngoingTransaction) {
        super.setOngoingTransaction(thereIsAOngoingTransaction);
        buttonStart.setEnabled(!thereIsAOngoingTransaction);
    }
}
