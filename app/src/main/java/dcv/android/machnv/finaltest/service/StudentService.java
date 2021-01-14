package dcv.android.machnv.finaltest.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dcv.android.machnv.finaltest.data.servicecore.PropertyEvent;
import dcv.android.machnv.finaltest.data.testhmiapplication.TestCapability;
import dcv.test.servicecore.IConfigurationService;
import dcv.test.servicecore.ICoreService;
import dcv.test.servicecore.IPropertyEventListener;
import dcv.test.servicecore.IPropertyService;
import dcv.test.testhmiapplication.IHMIListener;
import dcv.test.testhmiapplication.IStudentInterface;

public class StudentService extends Service {

    public static final String TAG = StudentService.class.getName();
    public static final int MESSAGE_EVENT_REGISTER_LISTENER = 10;
    public static final int MESSAGE_EVENT_UNREGISTER_LISTENER = 11;
    public static final int NUMBER_OF_SIGNAL_PER_MINUTE = 120; // 500ms/1signal --> 1 minute send 120 signal

    private boolean isCoreServiceConnected = false;
    private HandlerThread mHandlerThread;
    private Handler mHandlerEvent;
    private double[] arrConsumptionValue = new double[15];
    private int numberSignal = 0;
    private double currentConsumptionValue = 0;
    private boolean isFirstUnit = true;
    private boolean isFailedClick = false;

    private ICoreService mICoreService;
    private IPropertyService mIPropertyService;
    private IConfigurationService mIConfigurationService;
    private IHMIListener mIHMIListener;

    @Override
    public void onCreate() {
        super.onCreate();
        bindToCoreService();
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandlerEvent = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                handlerEventFromMessage(msg);
            }
        };
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isCoreServiceConnected) {
            unregisterProperty();
            mHandlerThread.quit();
            unbindService(mServiceConnectionToCoreService);
        }
    }

    private void bindToCoreService() {
        Intent intent = new Intent();
        intent.setAction("dcv.finaltest.BIND");
        intent.setPackage("dcv.test.servicecore");
        bindService(intent, mServiceConnectionToCoreService, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mServiceConnectionToCoreService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Connect to CoreService success!");
            mICoreService = ICoreService.Stub.asInterface(service);
            if (mICoreService != null) {
                isCoreServiceConnected = true;
                try {
                    mIPropertyService = mICoreService.getPropertyService();
                    mIConfigurationService = mICoreService.getConfigurationService();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                registerProperty();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isCoreServiceConnected = false;
        }
    };

    private IStudentInterface.Stub mBinder = new IStudentInterface.Stub() {
        @Override
        public void registerListener(IHMIListener listener) throws RemoteException {
            mIHMIListener = listener;
        }

        @Override
        public void unregisterListener(IHMIListener listener) throws RemoteException {
            mIHMIListener = null;
        }

        @Override
        public TestCapability getCapability() throws RemoteException {
            if (mIConfigurationService != null) {
                return new TestCapability(
                        mIConfigurationService.isSupport(IConfigurationService.CONFIG_DISTANCE),
                        mIConfigurationService.isSupport(IConfigurationService.CONFIG_CONSUMPTION),
                        mIConfigurationService.isSupport(IConfigurationService.CONFIG_RESET));
            }
            Log.d(TAG, "Not connect to ConfigurationService");
            return null;
        }

        @Override
        public void setDistanceUnit(int unit) throws RemoteException {
            PropertyEvent propertyDistanceEvent = new PropertyEvent(IPropertyService.PROP_DISTANCE_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            mIPropertyService.setProperty(IPropertyService.PROP_DISTANCE_UNIT, propertyDistanceEvent);
        }

        @Override
        public void setConsumptionUnit(int unit) throws RemoteException {
            PropertyEvent propertyConsumptionEven = new PropertyEvent(IPropertyService.PROP_CONSUMPTION_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            mIPropertyService.setProperty(IPropertyService.PROP_DISTANCE_UNIT, propertyConsumptionEven);
        }

        @Override
        public void resetData() throws RemoteException {
            isFailedClick = false;
            PropertyEvent propertyResetDataEvent = new PropertyEvent(IPropertyService.PROP_RESET, PropertyEvent.STATUS_AVAILABLE, true);
            mIPropertyService.setProperty(IPropertyService.PROP_RESET, propertyResetDataEvent);
        }
    };

    private void registerProperty() {
        if (mIPropertyService != null) {
            try {
                mIPropertyService.registerListener(IPropertyService.PROP_DISTANCE_UNIT, eventRegister);
                mIPropertyService.registerListener(IPropertyService.PROP_DISTANCE_VALUE, eventRegister);
                mIPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventRegister);
                mIPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventRegister);
                mIPropertyService.registerListener(IPropertyService.PROP_RESET, eventRegister);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    private IPropertyEventListener.Stub eventRegister = new IPropertyEventListener.Stub() {
        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_REGISTER_LISTENER;
            message.obj = event;
            mHandlerEvent.sendMessage(message);
        }

        @Override
        public void onError(int errorCode) throws RemoteException {
            //Không cần Handle
        }
    };

    private void unregisterProperty() {
        if (mIPropertyService != null) {
            try {
                mIPropertyService.unregisterListener(IPropertyService.PROP_DISTANCE_UNIT, eventUnregister);
                mIPropertyService.unregisterListener(IPropertyService.PROP_DISTANCE_VALUE, eventUnregister);
                mIPropertyService.unregisterListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventUnregister);
                mIPropertyService.unregisterListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventUnregister);
                mIPropertyService.unregisterListener(IPropertyService.PROP_RESET, eventUnregister);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    private IPropertyEventListener.Stub eventUnregister = new IPropertyEventListener.Stub() {
        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_UNREGISTER_LISTENER;
            message.obj = event;
            mHandlerEvent.sendMessage(message);
        }

        @Override
        public void onError(int errorCode) throws RemoteException {
            //Không cần Handle
        }
    };

    private void handlerEventFromMessage(Message msg) {
        if (msg.what == MESSAGE_EVENT_REGISTER_LISTENER) {
            if (mIHMIListener != null) {
                PropertyEvent propertyEvent = (PropertyEvent) msg.obj;
                if (propertyEvent.getStatus() == PropertyEvent.STATUS_AVAILABLE) {
                    switch (propertyEvent.getPropertyId()) {
                        case IPropertyService.PROP_DISTANCE_UNIT: {
                            try {
                                mIHMIListener.onDistanceUnitChanged((Integer) propertyEvent.getValue());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                        case IPropertyService.PROP_DISTANCE_VALUE: {
                            if (isFailedClick) break;
                            try {
                                mIHMIListener.onDistanceChanged((Double) propertyEvent.getValue());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                        case IPropertyService.PROP_CONSUMPTION_UNIT: {
                            if (isFailedClick) break;
                            try {
                                mIHMIListener.OnConsumptionUnitChanged((Integer) propertyEvent.getValue());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }

                            //Handle data on screen switch 2 state: KM_PER_L, L_PER_100KM
                            for (int i = 0; i < 15; i++) {
                                if (arrConsumptionValue[i] != 0) {
                                    if ((Integer) propertyEvent.getValue() == IPropertyService.KM_PER_L) {
                                        // convert from l/100km to km/l
                                        arrConsumptionValue[i] = 100 / arrConsumptionValue[i];
                                    } else if ((Integer) propertyEvent.getValue() == IPropertyService.L_PER_100KM) {
                                        // convert from km/l to l/100km
                                        arrConsumptionValue[i] = 100 / arrConsumptionValue[i];
                                    }
                                }
                            }
                            try {
                                mIHMIListener.onConsumptionChanged(arrConsumptionValue);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            numberSignal = 0;
                            currentConsumptionValue = 0;
                            break;
                        }
                        case IPropertyService.PROP_CONSUMPTION_VALUE: {
                            if (isFailedClick) break;
                            if (isFirstUnit) {
                                try {
                                    mIHMIListener.onConsumptionChanged(new double[15]);
                                    isFirstUnit = false;
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                            numberSignal++;
                            currentConsumptionValue += (double) propertyEvent.getValue();
                            if (numberSignal == NUMBER_OF_SIGNAL_PER_MINUTE) {
                                currentConsumptionValue = currentConsumptionValue / NUMBER_OF_SIGNAL_PER_MINUTE;
                                for (int i = 0; i < 14; i++) {
                                    arrConsumptionValue[i] = arrConsumptionValue[i + 1];
                                }
                                arrConsumptionValue[14] = currentConsumptionValue;
                                try {
                                    mIHMIListener.onConsumptionChanged(arrConsumptionValue);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                numberSignal = 0;
                                currentConsumptionValue = 0;
                            }
                            break;
                        }
                        case IPropertyService.PROP_RESET: {
                            if ((Boolean) propertyEvent.getValue()) {
                                arrConsumptionValue = new double[15];
                                numberSignal = 0;
                                currentConsumptionValue = 0;
                                try {
                                    mIHMIListener.onConsumptionChanged(arrConsumptionValue);
                                    mIHMIListener.onError(true);
                                    Thread.sleep(1000);
                                    mIHMIListener.onError(false);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                    }
                } else if (propertyEvent.getStatus() == PropertyEvent.STATUS_UNAVAILABLE) {

                    Log.d(TAG, "handlerEventFromMessage: " + propertyEvent.getValue());
                    try {
                        isFailedClick = true;
                        mIHMIListener.onError(true);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (msg.what == MESSAGE_EVENT_UNREGISTER_LISTENER) {
            //Không cần Handle
        }
    }
}
