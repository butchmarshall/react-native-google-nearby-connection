package com.butchmarshall.reactnative.google.nearby.connection;

import static com.butchmarshall.reactnative.google.nearby.connection.Constants.TAG;

import android.Manifest;
import android.hardware.Camera;

import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.PromiseImpl;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;

import android.app.Activity;

import android.net.Uri;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
/*import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;*/
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

public class NearbyConnectionModule extends ReactContextBaseJavaModule implements ActivityEventListener {
	ReactApplicationContext reactContext;

	/**
	 * These permissions are required before connecting to Nearby Connections. Only {@link
	 * Manifest.permission#ACCESS_COARSE_LOCATION} is considered dangerous, so the others should be
	 * granted just by having them in our AndroidManfiest.xml
	 */
	private static final String[] REQUIRED_PERMISSIONS =
		new String[] {
			Manifest.permission.BLUETOOTH,
			Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.ACCESS_WIFI_STATE,
			Manifest.permission.CHANGE_WIFI_STATE,
			Manifest.permission.ACCESS_COARSE_LOCATION,
		};

	private static final String[] REQUIRED_AUDIO_PERMISSIONS =
		new String[] {
			Manifest.permission.RECORD_AUDIO,
		};

	private static final String[] REQUIRED_STORAGE_PERMISSIONS =
		new String[] {
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
		};

	private static final String E_PERMISSIONS_MISSING = "E_PERMISSION_MISSING";
	private static final String E_CALLBACK_ERROR = "E_CALLBACK_ERROR";
	private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

	/** For recording audio as the user speaks. */
	@Nullable private AudioRecorder mRecorder;

	/**
	 * True if we are asking a discovered device to connect to us. While we ask, we cannot ask another
	 * device.
	 */
	private boolean mIsConnecting = false;

	/** True if we are discovering. */
	private boolean mIsDiscovering = false;

	/** True if we are advertising. */
	private boolean mIsAdvertising = false;

	private final Map<String, ConnectionsClient> mConnectionsClients = new HashMap<>();

	/** The devices we've discovered near us. */
	private final Map<String, Endpoint> mEndpoints = new HashMap<>();

	/** For recording audio as the user speaks. */
	private final Map<String, AudioRecorder> mRecorders = new HashMap<>();

	private final Map<String, Payload> mReceivedPayloads = new HashMap<>();
	private final SimpleArrayMap<String, String> mPayloadFileData = new SimpleArrayMap<>();

	private final Map<String, AudioPlayer> mAudioPlayers = new HashMap<>();

	/** Callbacks for connections to other devices. */
	private ConnectionLifecycleCallback getConnectionLifecycleCallback(final String serviceId, final String type) {
		final ConnectionLifecycleCallback mConnectionLifecycleCallback = new ConnectionLifecycleCallback() {
			@Override
			public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
				logD(
					String.format(
						"onConnectionInitiated(serviceId=%s, endpointId=%s, endpointName=%s)",
						serviceId, endpointId, connectionInfo.getEndpointName()));

				Endpoint endpoint = new Endpoint(serviceId, endpointId, connectionInfo.getEndpointName(), type);
				mEndpoints.put(serviceId+"_"+endpointId, endpoint);

				connectionInitiatedToEndpoint(serviceId, endpointId, connectionInfo);
			}

			@Override
			public void onConnectionResult(String endpointId, ConnectionResolution result) {
				logD(String.format("onConnectionResponse(serviceId=%s, endpointId=%s, result=%s)", serviceId, endpointId, result));

				// We're no longer connecting
				mIsConnecting = false;

				if (!result.getStatus().isSuccess()) {
					logW(
						String.format(
							"Connection failed. Received status %s.",
							NearbyConnectionModule.statusToString(result.getStatus())));

					endpointConnectionFailed(serviceId, endpointId, result.getStatus().getStatusCode());
					return;
				}
				connectedToEndpoint(serviceId, endpointId);
			}

			@Override
			public void onDisconnected(String endpointId) {
				logD(String.format("onDisconnected(serviceId=%s, endpointId=%s)", serviceId, endpointId));

				// TODO FIX THIS
				if (!mEndpoints.containsKey(serviceId+"_"+endpointId) || !mEndpoints.get(serviceId+"_"+endpointId).isConnected()) {
					logW("Unexpected disconnection from serviceId "+serviceId+" and endpoint " + endpointId);

					return;
				}
				closeMicrophone(serviceId, endpointId);

				disconnectedFromEndpoint(serviceId, endpointId);
			}
		};

		return mConnectionLifecycleCallback;
	}

	/** Callbacks for payloads (bytes of data) sent from another device to us. */
	private PayloadCallback getPayloadCallback(final String serviceId) {
		final PayloadCallback mPayloadCallback = new PayloadCallback() {
			@Override
			public void onPayloadReceived(String endpointId, Payload payload) {
				String payloadId = Long.toString(payload.getId());
				int payloadType = payload.getType();

				logD(String.format("onPayloadReceived(endpointId=%s, payload=%s, id=%s, type=%s)", endpointId, payload, payloadId, payloadType));

				mReceivedPayloads.put(serviceId+"_"+endpointId+"_"+payloadId, payload);

				// Track out-of-band filename transfers
				if (payloadType == Payload.Type.BYTES) {
					String bytes = "";

					try {
						bytes = new String(payload.asBytes(), "UTF-8");
					}
					catch (UnsupportedEncodingException e) {
						logW("onPayloadReceived failed converting payload asBytes.", e);
					}

					int colonIndex = bytes.indexOf(':');
					int filePayloadType = Integer.parseInt(bytes.substring(0, colonIndex));
					if (filePayloadType == Payload.Type.FILE) {
						bytes = bytes.substring(colonIndex + 1);

						colonIndex = bytes.indexOf(':');
						String filePayloadId = bytes.substring(0, colonIndex);
						String filename = bytes.substring(colonIndex + 1);

						logD(String.format("got filename for (payloadId=%s, filename=%s)", filePayloadId, filename));

						mPayloadFileData.put(filePayloadId, filename);
					}
				}

				onReceivePayload(serviceId, endpointId, payload);
			}

			@Override
			public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
				logD(String.format("onPayloadTransferUpdate(endpointId=%s, update=%s)", endpointId, update));

				onPayloadUpdate(serviceId, endpointId, update);
			}
		};

		return mPayloadCallback;
	};

	private EndpointDiscoveryCallback getEndpointDiscoveryCallback(final String serviceId) {
		final EndpointDiscoveryCallback mEndpointDiscoveryCallback = new EndpointDiscoveryCallback() {
			@Override
			public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                logD(
                    String.format(
                        "onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                        endpointId, info.getServiceId(), info.getEndpointName()));

				Endpoint existingEndpoint = mEndpoints.get(serviceId+"_"+endpointId);

				if (serviceId.equals(info.getServiceId()) && existingEndpoint == null || !existingEndpoint.isConnected()) {
					Endpoint endpoint = new Endpoint(serviceId, endpointId, info.getEndpointName(), "discovering");
					mEndpoints.put(serviceId+"_"+endpointId, endpoint);

					onEndpointDiscovered(serviceId, endpointId);
				}
			}

			@Override
			public void onEndpointLost(String endpointId) {
				logD(String.format("onEndpointLost(endpointId=%s)", endpointId));

				endpointLost(serviceId, endpointId);
			}
		};

		return mEndpointDiscoveryCallback;
	}

    public NearbyConnectionModule(ReactApplicationContext reactContext) {
        super(reactContext);
		this.reactContext = reactContext;

		onResume();

		setLifecycleListeners();
    }

	private void onResume() {
		registerAdvertisingStartingHandler();
		registerAdvertisingStartedHandler();
		registerAdvertisingStartFailedHandler();

		registerDiscoveryStartingHandler();
		registerDiscoveryStartedHandler();
		registerDiscoveryStartFailedHandler();

		registerEndpointDiscoveredHandler();
		registerEndpointLostHandler();
		registerConnectionInitiatedToEndpointHandler();
		registerConnectedToEndpointHandler();
		registerDisconnectedFromEndpointHandler();
		registerEndpointConnectionFailedHandler();
		registerOnReceivePayloadHandler();
		registerOnPayloadUpdateHandler();
		registerSendPayloadFailedHandler();
	}
	
	private void onPause() {
		unregisterAdvertisingStartingHandler();
		unregisterAdvertisingStartedHandler();
		unregisterAdvertisingStartFailedHandler();

		unregisterDiscoveryStartingHandler();
		unregisterDiscoveryStartedHandler();
		unregisterDiscoveryStartFailedHandler();

		unregisterEndpointDiscoveredHandler();
		unregisterEndpointLostHandler();
		unregisterConnectionInitiatedToEndpointHandler();
		unregisterConnectedToEndpointHandler();
		unregisterDisconnectedFromEndpointHandler();
		unregisterEndpointConnectionFailedHandler();
		unregisterOnReceivePayloadHandler();
		unregisterOnPayloadUpdateHandler();
		unregisterSendPayloadFailedHandler();
	}
	
	private void onDestroy() {
		
	}

	@Override
	public void onCatalystInstanceDestroy() {
		logW("onCatalystInstanceDestroy");
		for (ConnectionsClient mConnectionsClient : mConnectionsClients.values()) {
			mConnectionsClient.stopDiscovery();
			mConnectionsClient.stopAdvertising();
			mConnectionsClient.stopAllEndpoints();
		}
	}

    private void setLifecycleListeners() {

        this.reactContext.addLifecycleEventListener(new LifecycleEventListener() {
            @Override
            public void onHostResume() {
                logW("onHostResume");
                onResume();
            }

            @Override
            public void onHostPause() {
                logW("onHostPause");
                onPause();
            }

            @Override
            public void onHostDestroy() {
				logW("onHostDestroy");
                onDestroy();
            }
        });
    }

	/** Called when our Activity is first created. */
	protected ConnectionsClient getConnectionsClientSingleton(final String serviceId) {
		ConnectionsClient mConnectionsClient = mConnectionsClients.get(serviceId);
		if (mConnectionsClient == null) {
			logV("No ConnectionsClient exists for " + serviceId + ", initializing");

			final Activity currentActivity = getCurrentActivity();

			mConnectionsClient = Nearby.getConnectionsClient(currentActivity);
			mConnectionsClients.put(serviceId, mConnectionsClient);
		}

		return mConnectionsClient;
	}

    @Override
    public String getName() {
        return "NearbyConnection";
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {

    }

	/**
	 * Get all the payloads received by this device
	 */
	@ReactMethod
	public void payloads(final Promise promise) {
		WritableArray result = Arguments.createArray();

		for (String key : mReceivedPayloads.keySet()) {
			// Explode key to get serviceId, endpointId, payloadId
			//result.pushMap(endpoint.toWritableMap());
		}

		promise.resolve(result);
	}

	/**
	 * Get all the services discovered by this device
	 */
	@ReactMethod
	public void endpoints(final Promise promise) {
		WritableArray result = Arguments.createArray();

		for (Endpoint endpoint : mEndpoints.values()) {
			result.pushMap(endpoint.toWritableMap());
		}

		promise.resolve(result);
	}

	@ReactMethod
	public void rejectConnection(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		logV("rejecting connection from " + endpointId);
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		clientSingleton
			.rejectConnection(endpointId)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						ApiException apiException = (ApiException) e;

						logW("rejectConnection() failed.", e);
					}
				});
	}

	@ReactMethod
	public void acceptConnection(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		logV("acceptConnection(serviceId: "+serviceId+", endpointId:" + endpointId+")");
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		clientSingleton
			.acceptConnection(endpointId, getPayloadCallback(serviceId))
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						ApiException apiException = (ApiException) e;

						logW("acceptConnection(serviceId: "+serviceId+", endpointId:" + endpointId+") failed.", e);
					}
				});
	}

	@ReactMethod
	protected void startAdvertising(final String endpointName, final String serviceId, final int strategy) {
		mIsAdvertising = true;
		onAdvertisingStarting(endpointName, serviceId);

		Strategy finalStrategy = Strategy.P2P_CLUSTER;
		switch(strategy) {
			case 0: finalStrategy = Strategy.P2P_CLUSTER;
				break;
			case 1: finalStrategy = Strategy.P2P_STAR;
				break;
		}

		final Activity activity = getCurrentActivity();
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);
		final AdvertisingOptions advertisingOptions =  new AdvertisingOptions(finalStrategy);

        permissionsCheck(activity, Arrays.asList(getRequiredPermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				clientSingleton
					.startAdvertising(
						endpointName,
						serviceId,
						getConnectionLifecycleCallback(serviceId, "advertised"),
						advertisingOptions
					)
					.addOnSuccessListener(
						new OnSuccessListener<Void>() {
							@Override
							public void onSuccess(Void unusedResult) {
								logV("Now advertising endpoint " + endpointName + " with serviceId " + serviceId);
								onAdvertisingStarted(endpointName, serviceId);
							}
						}
					)
					.addOnFailureListener(
						new OnFailureListener() {
							@Override
							public void onFailure(@NonNull Exception e) {
								ApiException apiException = (ApiException) e;

								mIsAdvertising = false;
								logW("startAdvertising for endpointName "+ endpointName +" serviceId "+ serviceId +" failed.", e);
								onAdvertisingStartFailed(endpointName, serviceId, apiException.getStatusCode());
							}
						}
					);

                return null;
            }
        });
	}

	/** Stops discovery. */
	@ReactMethod
	public void stopAdvertising(final String serviceId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		logV("Stopping advertising endpoint " + serviceId);

		mIsAdvertising = false;
		clientSingleton.stopAdvertising();
	}

	/** Returns {@code true} if currently advertising. */
	@ReactMethod
	public boolean isAdvertising() {
		return mIsAdvertising;
	}

	@ReactMethod
	public void startDiscovering(final String serviceId, final int strategy) {
		mIsDiscovering = true;
		onDiscoveryStarting(serviceId);

		Strategy finalStrategy = Strategy.P2P_CLUSTER;
		switch(strategy) {
			case 0: finalStrategy = Strategy.P2P_CLUSTER;
				break;
			case 1: finalStrategy = Strategy.P2P_STAR;
				break;
		}

		final Activity activity = getCurrentActivity();
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);
		final DiscoveryOptions discoveryOptions = new DiscoveryOptions(finalStrategy);

        permissionsCheck(activity, Arrays.asList(getRequiredPermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				clientSingleton.startDiscovery(
					serviceId,
					getEndpointDiscoveryCallback(serviceId),
					discoveryOptions
				).addOnSuccessListener(
					new OnSuccessListener<Void>() {
						@Override
						public void onSuccess(Void unusedResult) {
							logV("Now discovering for serviceId "+ serviceId);
							onDiscoveryStarted(serviceId);
						}
					}
				).addOnFailureListener(
					new OnFailureListener() {
						@Override
						public void onFailure(@NonNull Exception e) {
							ApiException apiException = (ApiException) e;

							mIsDiscovering = false;
							logW("startDiscovering for serviceId "+serviceId+" failed.", e);
							onDiscoveryStartFailed(serviceId, apiException.getStatusCode());
						}
					}
				);

                return null;
            }
        });
	}

	/** Stops discovery. */
	@ReactMethod
	public void stopDiscovering(String serviceId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		logV("Stopping discovering endpoints " + serviceId);

		mIsDiscovering = false;
		clientSingleton.stopDiscovery();
	}

	/** Returns {@code true} if currently discovering. */
	@ReactMethod
	public boolean isDiscovering() {
		return mIsDiscovering;
	}

	/** Returns {@code true} if currently connecting. */
	@ReactMethod
	public boolean isConnecting() {
		return mIsConnecting;
	}

	/**
	 * Called when advertising starting
	 */
	private void onAdvertisingStarting(final String endpointName, final String serviceId) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStarting");
		Bundle bundle = new Bundle();
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mAdvertisingStartingReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "advertising_starting", out);
			}
		}
	};
	private void unregisterAdvertisingStartingHandler() {
		getReactApplicationContext().unregisterReceiver(mAdvertisingStartingReceiver);
	}
	private void registerAdvertisingStartingHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStarting");
		getReactApplicationContext().registerReceiver(mAdvertisingStartingReceiver, intentFilter);
	}

	/**
	 * Called when advertising started
	 */
	private void onAdvertisingStarted(final String endpointName, final String serviceId) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStarted");
		Bundle bundle = new Bundle();
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mAdvertisingStartedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "advertising_started", out);
			}
		}
	};
	private void unregisterAdvertisingStartedHandler() {
		getReactApplicationContext().unregisterReceiver(mAdvertisingStartedReceiver);
	}
	private void registerAdvertisingStartedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStarted");
		getReactApplicationContext().registerReceiver(mAdvertisingStartedReceiver, intentFilter);
	}

	/**
	 * Called when advertising failed to start
	 */
	private void onAdvertisingStartFailed(final String endpointName, final String serviceId, final int statusCode) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStartFailed");
		Bundle bundle = new Bundle();
		bundle.putInt("statusCode", statusCode);
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mAdvertisingStartFailedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				int statusCode = intent.getIntExtra("statusCode", -1);
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putInt("statusCode", statusCode);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "advertising_start_failed", out);
			}
		}
	};
	private void unregisterAdvertisingStartFailedHandler() {
		getReactApplicationContext().unregisterReceiver(mAdvertisingStartFailedReceiver);
	}
	private void registerAdvertisingStartFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.AdvertisingStartFailed");
		getReactApplicationContext().registerReceiver(mAdvertisingStartFailedReceiver, intentFilter);
	}

	/**
	 * Called when discovery starting
	 */
	private void onDiscoveryStarting(final String serviceId) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStarting");
		Bundle bundle = new Bundle();
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mDiscoveryStartingReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "discovery_starting", out);
			}
		}
	};
	private void unregisterDiscoveryStartingHandler() {
		getReactApplicationContext().unregisterReceiver(mDiscoveryStartingReceiver);
	}
	private void registerDiscoveryStartingHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStarting");
		getReactApplicationContext().registerReceiver(mDiscoveryStartingReceiver, intentFilter);
	}

	/**
	 * Called when discovery started
	 */
	private void onDiscoveryStarted(final String serviceId) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStarted");
		Bundle bundle = new Bundle();
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mDiscoveryStartedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "discovery_started", out);
			}
		}
	};
	private void unregisterDiscoveryStartedHandler() {
		getReactApplicationContext().unregisterReceiver(mDiscoveryStartedReceiver);
	}
	private void registerDiscoveryStartedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStarted");
		getReactApplicationContext().registerReceiver(mDiscoveryStartedReceiver, intentFilter);
	}

	/**
	 * Called when discovery started
	 */
	private void onDiscoveryStartFailed(final String serviceId, int statusCode) {
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStartFailed");
		Bundle bundle = new Bundle();
		bundle.putInt("statusCode", statusCode);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mDiscoveryStartFailedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				int statusCode = intent.getIntExtra("statusCode", -1);
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putInt("statusCode", statusCode);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "discovery_start_failed", out);
			}
		}
	};
	private void unregisterDiscoveryStartFailedHandler() {
		getReactApplicationContext().unregisterReceiver(mDiscoveryStartFailedReceiver);
	}
	private void registerDiscoveryStartFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.DiscoveryStartFailed");
		getReactApplicationContext().registerReceiver(mDiscoveryStartFailedReceiver, intentFilter);
	}

	/**
	 * Called when a payload was received
	 */
	private void onReceivePayload(final String serviceId, final String endpointId, Payload payload) {
		int payloadType = payload.getType();
		long payloadId = payload.getId();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.ReceivePayload");
		Bundle bundle = new Bundle();
		bundle.putString("serviceId", serviceId);
		bundle.putString("endpointId", endpointId);
		bundle.putInt("payloadType", payloadType);
		bundle.putString("payloadId", Long.toString(payloadId));
/*
		if (payloadType == Payload.Type.FILE) {
			long payloadSize = payload.getSize();
			bundle.putLong("payloadSize", payloadSize);
		}
*/
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mOnReceivePayloadReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String serviceId = intent.getStringExtra("serviceId");
				String endpointId = intent.getStringExtra("endpointId");
				int payloadType = intent.getIntExtra("payloadType", -1);
				String payloadId = intent.getStringExtra("payloadId");

				WritableMap out = Arguments.createMap();
				out.putString("serviceId", serviceId);
				out.putString("endpointId", endpointId);
				out.putInt("payloadType", payloadType);
				out.putString("payloadId", payloadId);

				if (payloadType == Payload.Type.FILE) {
					long payloadSize = intent.getLongExtra("payloadSize", -1);
					out.putDouble("payloadSize", payloadSize);
				}

				sendEvent(getReactApplicationContext(), "receive_payload", out);
			}
		}
	};
	private void unregisterOnReceivePayloadHandler() {
		getReactApplicationContext().unregisterReceiver(mOnReceivePayloadReceiver);
	}
	private void registerOnReceivePayloadHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.ReceivePayload");
		getReactApplicationContext().registerReceiver(mOnReceivePayloadReceiver, intentFilter);
	}

	/**
	 * Called when there is an update to a payload
	 */
	private void onPayloadUpdate(final String serviceId, final String endpointId, PayloadTransferUpdate update) {
		long bytesTransferred = update.getBytesTransferred();
		long totalBytes = update.getTotalBytes();
		long payloadId = update.getPayloadId();
		int payloadStatus = update.getStatus();
		int payloadHashCode = update.hashCode();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.PayloadUpdate");
		Bundle bundle = new Bundle();
		bundle.putString("serviceId", serviceId);
		bundle.putString("endpointId", endpointId);
		bundle.putLong("bytesTransferred", bytesTransferred);
		bundle.putLong("totalBytes", totalBytes);
		bundle.putString("payloadId", Long.toString(payloadId));
		bundle.putInt("payloadStatus", payloadStatus);
		bundle.putInt("payloadHashCode", payloadHashCode);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mRegisterOnPayloadUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String serviceId = intent.getStringExtra("serviceId");
				String endpointId = intent.getStringExtra("endpointId");
				long bytesTransferred = intent.getLongExtra("bytesTransferred", -1);
				long totalBytes = intent.getLongExtra("totalBytes", -1);
				String payloadId = intent.getStringExtra("payloadId");
				int payloadStatus = intent.getIntExtra("payloadStatus", -1);
				int payloadHashCode = intent.getIntExtra("payloadHashCode", -1);

				WritableMap out = Arguments.createMap();
				out.putString("serviceId", serviceId);
				out.putString("endpointId", endpointId);
				out.putDouble("bytesTransferred", bytesTransferred);
				out.putDouble("totalBytes", totalBytes);
				out.putString("payloadId", payloadId);
				out.putInt("payloadStatus", payloadStatus);
				out.putInt("payloadHashCode", payloadHashCode);

				sendEvent(getReactApplicationContext(), "payload_transfer_update", out);
			}
		}
	};
	private void unregisterOnPayloadUpdateHandler() {
		getReactApplicationContext().unregisterReceiver(mRegisterOnPayloadUpdateReceiver);
	}
	private void registerOnPayloadUpdateHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.PayloadUpdate");
		getReactApplicationContext().registerReceiver(mRegisterOnPayloadUpdateReceiver, intentFilter);
	}

	/**
	 * Called when sending payload fails
	 */
	private void onSendPayloadFailed(final String serviceId, final String endpointId, Payload payload, int statusCode) {
		int payloadType = payload.getType();
		long payloadId = payload.getId();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.SendPayloadFailed");
		Bundle bundle = new Bundle();
		bundle.putString("serviceId", serviceId);
		bundle.putString("endpointId", endpointId);
		bundle.putInt("statusCode", statusCode);
		bundle.putInt("payloadType", payloadType);
		bundle.putString("payloadId", Long.toString(payloadId));

		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mSendPayloadFailedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String serviceId = intent.getStringExtra("serviceId");
				String endpointId = intent.getStringExtra("endpointId");
				int statusCode = intent.getIntExtra("statusCode", -1);
				int payloadType = intent.getIntExtra("payloadType", -1);
				String payloadId = intent.getStringExtra("payloadId");

				WritableMap out = Arguments.createMap();
				out.putString("serviceId", serviceId);
				out.putString("endpointId", endpointId);
				out.putInt("statusCode", statusCode);
				out.putInt("payloadType", payloadType);
				out.putString("payloadId", payloadId);

				sendEvent(getReactApplicationContext(), "send_payload_failed", out);
			}
		}
	};
	private void unregisterSendPayloadFailedHandler() {
		getReactApplicationContext().unregisterReceiver(mSendPayloadFailedReceiver);
	}
	private void registerSendPayloadFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.SendPayloadFailed");
		getReactApplicationContext().registerReceiver(mSendPayloadFailedReceiver, intentFilter);
	}
	
	/**
	 * Called when connecting to an endpoint failed
	 */
	private void endpointConnectionFailed(final String serviceId, final String endpointId, final int statusCode) {
		Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		String endpointName = endpoint.getName();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.EndpointConnectionFailed");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		bundle.putInt("statusCode", statusCode);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mEndpointConnectionFailedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");
				int statusCode = intent.getIntExtra("statusCode", -1);

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);
				out.putInt("statusCode", statusCode);

				sendEvent(getReactApplicationContext(), "endpoint_connection_failed", out);
			}
		}
	};
	private void unregisterEndpointConnectionFailedHandler() {
		getReactApplicationContext().unregisterReceiver(mEndpointConnectionFailedReceiver);
	}
	private void registerEndpointConnectionFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.EndpointConnectionFailed");
		getReactApplicationContext().registerReceiver(mEndpointConnectionFailedReceiver, intentFilter);
	}

	/**
	 * Called when disconnected from an endpoint 
	 */
	private void disconnectedFromEndpoint(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);
		
		if (endpoint == null) {
			logD("disconnectedFromEndpoint unknown endpoint");
			return;
		}
		logD(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));

		String endpointName = endpoint.getName();

		endpoint.setConnected(false);

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.DisconnectedFromEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mDisconnectedFromEndpointReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);
				sendEvent(getReactApplicationContext(), "disconnected_from_endpoint", out);
			}
		}
	};
	private void unregisterDisconnectedFromEndpointHandler() {
		getReactApplicationContext().unregisterReceiver(mDisconnectedFromEndpointReceiver);
	}
	private void registerDisconnectedFromEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.DisconnectedFromEndpoint");
		getReactApplicationContext().registerReceiver(mDisconnectedFromEndpointReceiver, intentFilter);
	}

	/**
	 * Called when connected to a nearby advertiser 
	 */
	private void connectedToEndpoint(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		logD(String.format("connectedToEndpoint(endpoint=%s)", endpoint));

		String endpointName = endpoint.getName();

		endpoint.setConnected(true);

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.ConnectedToEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mConnectedToEndpointReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);
				
				sendEvent(getReactApplicationContext(), "connected_to_endpoint", out);
			}
		}
	};
	private void unregisterConnectedToEndpointHandler() {
		getReactApplicationContext().unregisterReceiver(mConnectedToEndpointReceiver);
	}
	private void registerConnectedToEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.ConnectedToEndpoint");
		getReactApplicationContext().registerReceiver(mConnectedToEndpointReceiver, intentFilter);
	}

	/**
	 * Called when a connection to a nearby advertiser is initiated
	 */
	private void connectionInitiatedToEndpoint(final String serviceId, final String endpointId, final ConnectionInfo connectionInfo) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		String authenticationToken = connectionInfo.getAuthenticationToken();
		String endpointName = connectionInfo.getEndpointName();
		Boolean incomingConnection = connectionInfo.isIncomingConnection();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.ConnectionInitiatedToEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		bundle.putString("authenticationToken", authenticationToken);
		bundle.putBoolean("incomingConnection", incomingConnection);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mConnectionInitiatedToEndpointReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");
				String authenticationToken = intent.getStringExtra("authenticationToken");
				Boolean incomingConnection = intent.getBooleanExtra("incomingConnection", false);

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);
				out.putString("authenticationToken", authenticationToken);
				out.putBoolean("incomingConnection", incomingConnection);

				sendEvent(getReactApplicationContext(), "connection_initiated_to_endpoint", out);
			}
		}
	};
	private void unregisterConnectionInitiatedToEndpointHandler() {
		getReactApplicationContext().unregisterReceiver(mConnectionInitiatedToEndpointReceiver);
	}
	private void registerConnectionInitiatedToEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.ConnectionInitiatedToEndpoint");
		getReactApplicationContext().registerReceiver(mConnectionInitiatedToEndpointReceiver, intentFilter);
	}

	/**
	 * Called when a nearby endpoint is lost
	 */
	private void endpointLost(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		String endpointName = endpoint.getName();
		
		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.EndPointLost");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("serviceId", serviceId);
		bundle.putString("endpointName", endpointName);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mEndpointLostReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "endpoint_lost", out);
			}
		}
	};
	private void unregisterEndpointLostHandler() {
		getReactApplicationContext().unregisterReceiver(mEndpointLostReceiver);
	}
	private void registerEndpointLostHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.EndPointLost");
		getReactApplicationContext().registerReceiver(mEndpointLostReceiver, intentFilter);
	}

	/**
	 * Called when a remote endpoint is discovered.
     */
	private void onEndpointDiscovered(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);

		String endpointName = endpoint.getName();

		// We found an advertiser!
		// stopDiscovering(serviceId); - should we actually stop discovering?  Maybe more peeps!

		// Broadcast endpoint discovered
		Intent i = new Intent("com.butchmarshall.reactnative.google.nearby.connection.EndPointDiscovered");
		Bundle bundle = new Bundle();
		bundle.putString("endpointName", endpointName);
		bundle.putString("serviceId", serviceId);
		bundle.putString("endpointId", endpointId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private BroadcastReceiver mEndpointDiscoveredReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (getReactApplicationContext().hasActiveCatalystInstance()) {
				String endpointId = intent.getStringExtra("endpointId");
				String endpointName = intent.getStringExtra("endpointName");
				String serviceId = intent.getStringExtra("serviceId");

				WritableMap out = Arguments.createMap();
				out.putString("endpointId", endpointId);
				out.putString("endpointName", endpointName);
				out.putString("serviceId", serviceId);

				sendEvent(getReactApplicationContext(), "endpoint_discovered", out);
			}
		}
	};
	private void unregisterEndpointDiscoveredHandler() {
		getReactApplicationContext().unregisterReceiver(mEndpointDiscoveredReceiver);
	}
	private void registerEndpointDiscoveredHandler() {
		IntentFilter intentFilter = new IntentFilter("com.butchmarshall.reactnative.google.nearby.connection.EndPointDiscovered");
		getReactApplicationContext().registerReceiver(mEndpointDiscoveredReceiver, intentFilter);
	}

	/**
	 * Sends a connection request to the endpoint. Either {@link #onConnectionInitiated(Endpoint,
	 * ConnectionInfo)} or {@link #onConnectionFailed(Endpoint)} will be called once we've found out
	 * if we successfully reached the device.
	 */
	@ReactMethod
	public void connectToEndpoint(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);
		if (endpoint == null) {
			logW("connectToEndpoint failed to service "+serviceId+" and endpoint "+endpointId+", could not find endpoint");
			return;
		}

		final String endpointName = endpoint.getName();

		logV("connectToEndpoint request to service "+serviceId+" and endpoint " + endpointId);

		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		// Mark ourselves as connecting so we don't connect multiple times
		mIsConnecting = true;

		// Ask to connect
		clientSingleton
			.requestConnection(endpointName, endpointId, getConnectionLifecycleCallback(serviceId, "discovering"))
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						ApiException apiException = (ApiException) e;

						logW("connectToEndpoint to service "+serviceId+" and endpoint " + endpointId+" failed.", e);
						mIsConnecting = false;
						endpointConnectionFailed(serviceId, endpointId, apiException.getStatusCode());
					}
				}
			);
	}

	/** Disconnects from the given endpoint. */
	@ReactMethod
	public void disconnectFromEndpoint(final String serviceId, final String endpointId) {
		final Endpoint endpoint = mEndpoints.get(serviceId+"_"+endpointId);
		if (endpoint == null) {
			logW("disconnectFromEndpoint failed from service "+serviceId+" and endpoint "+endpointId);
			return;
		}

		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		clientSingleton.disconnectFromEndpoint(endpointId);

		disconnectedFromEndpoint(serviceId, endpointId);
	}

	/** Removes a payload */
	@ReactMethod
	public void removePayload(final String serviceId, final String endpointId, final String payloadId) {
		mReceivedPayloads.remove(serviceId+"_"+endpointId+"_"+payloadId);
	}

	/** Send a file to a connected device. */
	@ReactMethod
	public void sendFile(final String serviceId, final String endpointId, final String path, final String metadata) {
		Uri uri = Uri.parse(path);

		logV("sendFile to service "+serviceId+" and endpoint " + endpointId + ", "+ uri);

		try {
			// Open the ParcelFileDescriptor for this URI with read access.
			ParcelFileDescriptor pfd = getReactApplicationContext().getContentResolver().openFileDescriptor(uri, "r");
			Payload filePayload = Payload.fromFile(pfd);

			String payloadMessage = Payload.Type.FILE+":"+filePayload.getId() + ":" + uri.getLastPathSegment() + ":" + metadata;
			sendPayload(serviceId, endpointId, Payload.fromBytes(payloadMessage.getBytes("UTF-8")));

			sendPayload(serviceId, endpointId, filePayload);
		}
		catch (FileNotFoundException e)   {
			logW("sendFile() failed.", e);
		}
		catch(IOException e) {
			logW("sendFile() failed.", e);
		}
	}

	/** Send bytes to an endpoint */
	@ReactMethod
	public void sendBytes(final String serviceId, final String endpointId, final String bytes) {
		logV("sendBytes to service "+serviceId+" and endpoint " + endpointId +" byte payload "+bytes);

		try {
			String payloadMessage = Payload.Type.BYTES+":"+ bytes;

			sendPayload(serviceId, endpointId, Payload.fromBytes(payloadMessage.getBytes("UTF-8")));
		}
		catch(IOException e) {
			logW("sendBytes() failed.", e);
		}
	}

	/** Read bytes. */
	@ReactMethod
	public void readBytes(final String serviceId, final String endpointId, final String payloadId, final Promise promise) {
		logV("readBytes from service "+serviceId+" and endpoint " + endpointId + " and payload "+Long.parseLong(payloadId, 10)+" ");

		Payload payload = mReceivedPayloads.get(serviceId+"_"+endpointId+"_"+Long.parseLong(payloadId, 10));
		if (payload == null) {
			logV("Cannot find payload.");
			return;
		}
		if (payload.getType() != Payload.Type.BYTES) {
			logV("Cannot get, not bytes.");
			return;
		}

		try {
			String bytes = new String(payload.asBytes(), "UTF-8");

			int colonIndex = bytes.indexOf(':');
			int payloadType = Integer.parseInt(bytes.substring(0, colonIndex));
			bytes = bytes.substring(colonIndex + 1);

			WritableMap out = Arguments.createMap();
			out.putInt("type", payloadType);

			// Simple bytes payload
			if (payloadType == Payload.Type.BYTES) {
				out.putString("bytes", bytes);
			}
			// Represents a file or stream payload
			else if (payloadType == Payload.Type.FILE || payloadType == Payload.Type.STREAM) {
				// Get payloadId of payload represented
				colonIndex = bytes.indexOf(':');
				out.putString("payloadId", bytes.substring(0, colonIndex));

				bytes = bytes.substring(colonIndex + 1);
				colonIndex = bytes.indexOf(':');

				if (payloadType == Payload.Type.STREAM) {
					out.putString("streamType", bytes.substring(0, colonIndex));
				}
				else {
					out.putString("filename", bytes.substring(0, colonIndex));
				}
				out.putString("metadata", bytes.substring(colonIndex + 1));
			}

			promise.resolve(out);
		}
		catch (UnsupportedEncodingException ex) {
			promise.reject("");
		}
	}

	/** File a file to a uri. */
	@ReactMethod
	public void saveFile(final String serviceId, final String endpointId, final String payloadId, final Promise promise) {
		logV("saveFile from service "+serviceId+" and endpoint " + endpointId + " and payload "+payloadId);

		final Payload payload = mReceivedPayloads.get(serviceId+"_"+endpointId+"_"+payloadId);
		if (payload == null) {
			logV("Cannot find payload.");
			promise.reject("");
			return;
		}
		if (payload.getType() != Payload.Type.FILE) {
			logV("Cannot save, not a file.");
			promise.reject("");
			return;
		}

		final String payloadFileData = mPayloadFileData.get(payloadId);
		int colonIndex = payloadFileData.indexOf(':');
		final String payloadFilename = payloadFileData.substring(0, colonIndex);
		final String payloadMetadata = payloadFileData.substring(colonIndex+1);

		if (payloadFilename == null) {
			logV("Cannot find filename for "+payloadId);
			promise.reject("");
			return;
		}

		final Activity activity = getCurrentActivity();

		permissionsCheck(activity, Arrays.asList(getRequiredStoragePermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				File payloadFile = payload.asFile().asJavaFile();
				if (payloadFile == null) {
					logV("Cannot convert to file.");
					promise.reject("");
				}
				else {
					File destPath = payloadFile.getParentFile();
					String extension = NearbyConnectionModule.getFileExtension(payloadFilename);

					File destFile = NearbyConnectionModule.getUniqueFile(destPath, extension);

					payloadFile.renameTo(destFile);

					String destFilePath = destFile.getPath();
					logV("file saved to "+destFilePath);

					WritableMap out = Arguments.createMap();
					out.putString("path", destFilePath);
					out.putString("originalFilename", payloadFilename);
					out.putString("metadata", payloadMetadata);

					promise.resolve(out);
				}

				return null;
			}
		});
	}

	/** Send sound from the microphone and stream to a connected device. */
	@ReactMethod
	public void openMicrophone(final String serviceId, final String endpointId, final String metadata) {
		logV("openMicrophone to service "+serviceId+" and endpoint " + endpointId);

		final Activity activity = getCurrentActivity();

		permissionsCheck(activity, Arrays.asList(getRequiredAudioPermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				try {
					ParcelFileDescriptor[] payloadPipe = ParcelFileDescriptor.createPipe();

					Payload streamPayload = Payload.fromStream(payloadPipe[0]);

					// Send payload indicating this is an audio stream
					String payloadMessage = Payload.Type.STREAM+":"+streamPayload.getId() + ":audio:" + metadata;
					sendPayload(serviceId, endpointId, Payload.fromBytes(payloadMessage.getBytes("UTF-8")));

					// Send the first half of the payload (the read side) to Nearby Connections.
					sendPayload(serviceId, endpointId, streamPayload);

					// Use the second half of the payload (the write side) in AudioRecorder.
					AudioRecorder recorder = new AudioRecorder(payloadPipe[1]);

					mRecorders.put(serviceId+"_"+endpointId, recorder);

					recorder.start();
				}
				catch (IOException e) {
					logW("openMicrophone error", e);
				}

				return null;
			}
		});
	}

	/** Stops streaming sound from the microphone. */
	@ReactMethod
	private void closeMicrophone(final String serviceId, String endpointId) {
		logV("closeMicrophone to service "+serviceId+" and endpoint " + endpointId);

		AudioRecorder recorder = mRecorders.remove(serviceId+"_"+endpointId);

		if (recorder != null) {
			recorder.stop();
			recorder = null;
		}
	}

	/** Starts streaming sound from an endpoint. */
	@ReactMethod
	private void startPlayingAudioStream(final String serviceId, final String endpointId, final String payloadId) {
		logV("startPlayingAudioStream from service "+serviceId+" and endpoint " + endpointId + " and payload "+payloadId);

		Payload payload = mReceivedPayloads.get(serviceId+"_"+endpointId+"_"+payloadId);

		if (payload.getType() != Payload.Type.STREAM) {
			logV("startPlayingAudioStream from service "+serviceId+" and endpoint " + endpointId +" and payload "+payloadId+" failure.  Not a stream.");
			return;
		}
		final Activity activity = getCurrentActivity();

		AudioPlayer player = new AudioPlayer(payload.asStream().asInputStream()) {
			@WorkerThread
			@Override
			protected void onFinish() {
				activity.runOnUiThread(
					new Runnable() {
						@UiThread
						@Override
						public void run() {
							logV("audioPlayer onFinish "+serviceId+" and endpoint " + endpointId + " and payload "+payloadId);
							mAudioPlayers.remove(serviceId+"_"+endpointId+"_"+payloadId);
						}
					}
				);
			}
		};
		mAudioPlayers.put(serviceId+"_"+endpointId+"_"+payloadId, player);

		player.start();
	}

	@ReactMethod
	private void stopPlayingAudioStream(final String serviceId, String endpointId, final String payloadId) {
		logV("stopPlayingAudioStream from service "+serviceId+" and endpoint " + endpointId + " and payload "+payloadId);

		AudioPlayer player = mAudioPlayers.remove(serviceId+"_"+endpointId+"_"+payloadId);
		if (player == null) {
			return;
		}

		player.stop();
	}

	/** @return True if currently streaming from the microphone. */
	@ReactMethod
	private boolean isMicrophoneOpen() {
		for (AudioRecorder recorder : mRecorders.values()) {
			if (recorder != null && recorder.isRecording()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return All permissions required for the app connectivity to properly function.
	 */
	protected String[] getRequiredPermissions() {
		return REQUIRED_PERMISSIONS;
	}

	/**
	 * @return All permissions required audio transfer to properly function
	 */
	protected String[] getRequiredAudioPermissions() {
		return REQUIRED_AUDIO_PERMISSIONS;
	}

	/**
	 * @return All permissions required for file transfer to properly function
	 */
	protected String[] getRequiredStoragePermissions() {
		return REQUIRED_STORAGE_PERMISSIONS;
	}
	
	/**
	* Returns {@code true} if the app was granted all the permissions. Otherwise, returns {@code
	* false}.
	*/
	public static boolean hasPermissions(Context context, String... permissions) {
		for (String permission : permissions) {
			if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

    private void permissionsCheck(final Activity activity, final List<String> requiredPermissions, final Callable<Void> callback) {

        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            int status = ActivityCompat.checkSelfPermission(activity, permission);
            if (status != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ((PermissionAwareActivity) activity).requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), 1, new PermissionListener() {

                @Override
                public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                    if (requestCode == 1) {

                        for (int grantResult : grantResults) {
                            if (grantResult == PackageManager.PERMISSION_DENIED) {
                                return true;
                            }
                        }

                        try {
                            callback.call();
                        } catch (Exception e) {
							logV("permissionsCheck error.");
                        }
                    }

                    return true;
                }
            });

            return;
        }

        // all permissions granted
        try {
            callback.call();
        } catch (Exception e) {
			logV("permissionsCheck error.");
        }
    }

	/**
	 * Sends a {@link Payload} to all currently connected endpoints.
	 *
	 * @param payload The data you want to send.
	 */
	protected void sendPayload(final String serviceId, final String endpointId, final Payload payload) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton(serviceId);

		clientSingleton
			.sendPayload(endpointId, payload)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						ApiException apiException = (ApiException) e;

						logW("sendPayload() failed.", e);

						onSendPayloadFailed(serviceId, endpointId, payload, apiException.getStatusCode());
					}
				});
	}

	/**
	 * send a JS event
	 **/
	protected static void sendEvent(final ReactContext context, final String eventName, Object body) {
		if (context != null) {
			context
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
				.emit(eventName, body);
		} else {
			Log.v(TAG, "Missing context - cannot send event!");
		}
	}

	/** A safe way to get an instance of the Camera object. */
	private static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	/** Check if this device has a camera */
	private static boolean hasCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/**
	 * Transforms a {@link Status} into a English-readable message for logging.
	 *
	 * @param status The current status
	 * @return A readable String. eg. [404]File not found.
	 */
	private static String statusToString(Status status) {
	  return String.format(
		  Locale.US,
		  "[%d]%s",
		  status.getStatusCode(),
		  status.getStatusMessage() != null
			  ? status.getStatusMessage()
			  : ConnectionsStatusCodes.getStatusCodeString(status.getStatusCode()));
	}
	
	@CallSuper
	protected void logV(String msg) {
		Log.v(TAG, msg);
	}

	@CallSuper
	protected void logD(String msg) {
		Log.d(TAG, msg);
	}

	@CallSuper
	protected void logW(String msg) {
		Log.w(TAG, msg);
	}

	@CallSuper
	protected void logW(String msg, Throwable e) {
		Log.w(TAG, msg, e);
	}

	@CallSuper
	protected void logE(String msg, Throwable e) {
		Log.e(TAG, msg, e);
	}

	private static File getUniqueFile(File directory, String extension) {
		return new File(directory, new StringBuilder()
			.append(UUID.randomUUID())
			.append(".")
			.append(extension).toString()
		);
	}

	private static String getFileExtension(String name) {
		try {
			return name.substring(name.lastIndexOf(".") + 1);
		} catch (Exception e) {
			return "";
		}
	}

	/** Represents a device we can talk to. */
	protected static class Endpoint {
		@NonNull private final String serviceId;
		@NonNull private final String id;
		@NonNull private final String name;
		@NonNull private final String type;
		@NonNull private Boolean connected;

		private Endpoint(@NonNull String serviceId, @NonNull String id, @NonNull String name, @NonNull String type) {
			this.serviceId = serviceId;
			this.id = id;
			this.name = name;
			this.type = type;
			this.connected = false;
		}

		public WritableMap toWritableMap() {
			WritableMap out = Arguments.createMap();

			out.putString("serviceId", serviceId);
			out.putString("endpointId", id);
			out.putString("endpointName", name);
			out.putBoolean("connected", connected);
			out.putString("type", type);

			return out;
		}
		
		public void setConnected(final Boolean newValue) {
			this.connected = newValue;
		}

		@NonNull
		public Boolean isConnected() {
			return connected;
		}

		/**
		 * Discovered or advertised
		 */
		@NonNull
		public String getType() {
			return type;
		}

		@NonNull
		public String getServiceId() {
			return serviceId;
		}
		
		@NonNull
		public String getId() {
			return id;
		}

		@NonNull
		public String getName() {
		  return name;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Endpoint) {
				Endpoint other = (Endpoint) obj;
				return id.equals(other.id);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}

		@Override
		public String toString() {
			return String.format("Endpoint{id=%s, name=%s}", id, name);
		}
	}
}