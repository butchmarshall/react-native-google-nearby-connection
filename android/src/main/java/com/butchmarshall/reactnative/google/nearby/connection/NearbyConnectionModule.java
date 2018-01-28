package com.butchmarshall.reactnative.google.nearby.connection;

import static com.butchmarshall.reactnative.google.nearby.connection.Constants.TAG;

import android.Manifest;

import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.PromiseImpl;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import android.app.Activity;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.api.Status;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
			Manifest.permission.RECORD_AUDIO,
			Manifest.permission.BLUETOOTH,
			Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.ACCESS_WIFI_STATE,
			Manifest.permission.CHANGE_WIFI_STATE,
			Manifest.permission.ACCESS_COARSE_LOCATION,
		};

	private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
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

	private ConnectionsClient mConnectionsClient = null;

	/** The devices we've discovered near us. */
	private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();

	/**
	 * The devices we have pending connections to. They will stay pending until we call {@link
	 * #acceptConnection(Endpoint)} or {@link #rejectConnection(Endpoint)}.
	 */
	private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

	/**
	 * The devices we are currently connected to. For advertisers, this may be large. For discoverers,
	 * there will only be one entry in this map.
	 */
	private final Map<String, Endpoint> mEstablishedConnections = new HashMap<>();
	
	private final Map<String, Payload> mReceivedPayloads = new HashMap<>();
	private final Map<String, AudioPlayer> mAudioPlayers = new HashMap<>();

	/** Callbacks for connections to other devices. */
	private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
		new ConnectionLifecycleCallback() {
			@Override
			public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
				Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
				mPendingConnections.put(endpointId, endpoint);

				connectionInitiatedToEndpoint(endpoint, connectionInfo);
			}

			@Override
			public void onConnectionResult(String endpointId, ConnectionResolution result) {
				// We're no longer connecting
				mIsConnecting = false;

				if (!result.getStatus().isSuccess()) {
					mPendingConnections.remove(endpointId);
					endpointConnectionFailed(endpointId, result.getStatus().getStatusCode());
					return;
				}
				connectedToEndpoint(mPendingConnections.remove(endpointId));
			}

			@Override
			public void onDisconnected(String endpointId) {
				if (!mEstablishedConnections.containsKey(endpointId)) {
					return;
				}

				disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));
			}
		};
	
	/** Callbacks for payloads (bytes of data) sent from another device to us. */
	private final PayloadCallback mPayloadCallback =
		new PayloadCallback() {
			@Override
			public void onPayloadReceived(String endpointId, Payload payload) {
				onReceivePayload(mEstablishedConnections.get(endpointId), payload);
			}

			@Override
			public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
				onPayloadUpdate(endpointId, update);
			}
		};

	private final EndpointDiscoveryCallback mEndpointDiscoveryCallback = new EndpointDiscoveryCallback() {
			@Override
			public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
				if (getServiceId().equals(info.getServiceId())) {
					Endpoint endpoint = new Endpoint(endpointId, info.getEndpointName());
					mDiscoveredEndpoints.put(endpointId, endpoint);
					onEndpointDiscovered(endpoint);
				}
			}

			@Override
			public void onEndpointLost(String endpointId) {
				//Log.d("NearbyConnectionModule", "ENDPOINT LOST!");
				onEndpointLost(endpointId);
			}
		};
		
    public NearbyConnectionModule(ReactApplicationContext reactContext) {
        super(reactContext);
		this.reactContext = reactContext;

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
    }

	/** Called when our Activity is first created. */
	protected ConnectionsClient getConnectionsClientSingleton() {
		if (mConnectionsClient == null) {
			final Activity currentActivity = getCurrentActivity();

			mConnectionsClient = Nearby.getConnectionsClient(currentActivity);
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

	/* Gets the serviceId to be used by the Nearby package */
	public String getServiceId() {
		return this.reactContext.getApplicationContext().getPackageName();
	}

	public Strategy getStrategy() {
		return STRATEGY;
	}

    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {

    }

	@ReactMethod
	public void rejectConnection(String endpointId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		clientSingleton
			.rejectConnection(endpointId)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						//logW("acceptConnection() failed.", e);
					}
				});
	}

	@ReactMethod
	public void acceptConnection(String endpointId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		clientSingleton
			.acceptConnection(endpointId, mPayloadCallback)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						//logW("acceptConnection() failed.", e);
					}
				});
	}

	@ReactMethod
	protected void startAdvertising() {
		mIsAdvertising = true;
		onAdvertisingStarting();

		final String localEndpointName = getName();
		final String serviceId = getServiceId();
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();
		final AdvertisingOptions advertisingOptions =  new AdvertisingOptions(getStrategy());

		clientSingleton
			.startAdvertising(
				localEndpointName,
				serviceId,
				mConnectionLifecycleCallback,
				advertisingOptions
			)
			.addOnSuccessListener(
				new OnSuccessListener<Void>() {
					@Override
					public void onSuccess(Void unusedResult) {
						onAdvertisingStarted();
					}
				}
			)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						mIsAdvertising = false;

						onAdvertisingStartFailed(e.getMessage());
					}
				}
			);
	}

	/** Stops discovery. */
	@ReactMethod
	public void stopAdvertising() {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		mIsDiscovering = false;
		clientSingleton.stopAdvertising();
	}

	/** Returns {@code true} if currently advertising. */
	@ReactMethod
	public boolean isAdvertising() {
		return mIsAdvertising;
	}

	/**
	 * Sets the device to discovery mode. It will now listen for devices in advertising mode. Either
	 * {@link #onDiscoveryStarted()} or {@link #onDiscoveryFailed()} will be called once we've found
	 * out if we successfully entered this mode.
	 */
	@ReactMethod
	public void startDiscovering(final Promise promise) {
		mIsDiscovering = true;
		mDiscoveredEndpoints.clear();

		final String serviceId = getServiceId();
		final Activity activity = getCurrentActivity();
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();
		final DiscoveryOptions discoveryOptions = new DiscoveryOptions(getStrategy());

        permissionsCheck(activity, promise, Arrays.asList(getRequiredPermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				clientSingleton.startDiscovery(
					getServiceId(),
					mEndpointDiscoveryCallback,
					discoveryOptions
				).addOnSuccessListener(
					new OnSuccessListener<Void>() {
						@Override
						public void onSuccess(Void unusedResult) {
							onDiscoveryStarted();
						}
					}
				).addOnFailureListener(
					new OnFailureListener() {
						@Override
						public void onFailure(@NonNull Exception e) {
							mIsDiscovering = false;
							onDiscoveryStartFailed(e.getMessage());
						}
					}
				);

                return null;
            }
        });
	}

	/** Stops discovery. */
	@ReactMethod
	public void stopDiscovering() {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

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
	private void onAdvertisingStarting() {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.AdvertisingStarting");
		Bundle bundle = new Bundle();
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerAdvertisingStartingHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.AdvertisingStarting");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					sendEvent(getReactApplicationContext(), "advertising_starting", null);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when advertising started
	 */
	private void onAdvertisingStarted() {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.AdvertisingStarted");
		Bundle bundle = new Bundle();
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerAdvertisingStartedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.AdvertisingStarted");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					sendEvent(getReactApplicationContext(), "advertising_started", null);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when advertising failed to start
	 */
	private void onAdvertisingStartFailed(String reason) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.AdvertisingStartFailed");
		Bundle bundle = new Bundle();
		bundle.putString("reason", reason);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerAdvertisingStartFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.AdvertisingStartFailed");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String reason = intent.getStringExtra("reason");
					sendEvent(getReactApplicationContext(), "advertising_start_failed", reason);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when discovery starting
	 */
	private void onDiscoveryStarting() {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.DiscoveryStarting");
		Bundle bundle = new Bundle();
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerDiscoveryStartingHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.DiscoveryStarting");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					sendEvent(getReactApplicationContext(), "discovery_starting", null);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when discovery started
	 */
	private void onDiscoveryStarted() {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.DiscoveryStarted");
		Bundle bundle = new Bundle();
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerDiscoveryStartedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.DiscoveryStarted");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					sendEvent(getReactApplicationContext(), "discovery_started", null);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when discovery started
	 */
	private void onDiscoveryStartFailed(String reason) {
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.DiscoveryStartFailed");
		Bundle bundle = new Bundle();
		bundle.putString("reason", reason);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerDiscoveryStartFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.DiscoveryStartFailed");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String reason = intent.getStringExtra("reason");
					sendEvent(getReactApplicationContext(), "discovery_start_failed", reason);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when a payload was received
	 */
	private void onReceivePayload(Endpoint endpoint, Payload payload) {
		String endpointId = endpoint.getId();
		int payloadType = payload.getType();
		long payloadId = payload.getId();

		mReceivedPayloads.put(endpointId, payload);

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.ReceivePayload");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putInt("payloadType", payloadType);
		bundle.putLong("payloadId", payloadId);
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

	private void registerOnReceivePayloadHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.ReceivePayload");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					int payloadType = intent.getIntExtra("payloadType", -1);
					long payloadId = intent.getLongExtra("payloadId", -1);

					WritableMap out = Arguments.createMap();
					out.putString("endpointId", endpointId);
					out.putInt("payloadType", payloadType);
					out.putDouble("payloadId", payloadId);

					if (payloadType == Payload.Type.FILE) {
						long payloadSize = intent.getLongExtra("payloadSize", -1);
						out.putDouble("payloadSize", payloadSize);
					}

					sendEvent(getReactApplicationContext(), "receive_payload", out);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when there is an update to a payload
	 */
	private void onPayloadUpdate(String endpointId, PayloadTransferUpdate update) {
		long bytesTransferred = update.getBytesTransferred();
		long totalBytes = update.getTotalBytes();
		long payloadId = update.getPayloadId();
		int payloadStatus = update.getStatus();
		int hashCode = update.hashCode();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.ReceivePayload");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putLong("bytesTransferred", bytesTransferred);
		bundle.putLong("totalBytes", totalBytes);
		bundle.putLong("payloadId", payloadId);
		bundle.putInt("payloadStatus", payloadStatus);
		bundle.putInt("payloadHashCode", hashCode);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerOnPayloadUpdateHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.PayloadUpdate");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					long bytesTransferred = intent.getLongExtra("bytesTransferred", -1);
					long totalBytes = intent.getLongExtra("totalBytes", -1);
					long payloadId = intent.getLongExtra("payloadId", -1);
					int payloadStatus = intent.getIntExtra("payloadStatus", -1);
					int payloadHashCode = intent.getIntExtra("payloadHashCode", -1);

					WritableMap out = Arguments.createMap();
					out.putString("endpointId", endpointId);
					out.putDouble("bytesTransferred", bytesTransferred);
					out.putDouble("totalBytes", totalBytes);
					out.putDouble("payloadId", payloadId);
					out.putInt("payloadStatus", payloadStatus);
					out.putInt("payloadHashCode", payloadHashCode);

					sendEvent(getReactApplicationContext(), "payload_update", out);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when connecting to an endpoint failed
	 */
	private void endpointConnectionFailed(final String endpointId, final int statusCode) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.EndpointConnectionFailed");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putInt("statusCode", statusCode);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerEndpointConnectionFailedHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.EndpointConnectionFailed");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					int statusCode = intent.getIntExtra("statusCode", -1);

					WritableMap out = Arguments.createMap();
					out.putString("endpointId", endpointId);
					out.putInt("statusCode", statusCode);

					sendEvent(getReactApplicationContext(), "endpoint_connection_failed", out);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when disconnected from an endpoint 
	 */
	private void disconnectedFromEndpoint(Endpoint endpoint) {
		String endpointId = endpoint.getId();

		mEstablishedConnections.remove(endpointId);

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.DisconnectedFromEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerDisconnectedFromEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.DisconnectedFromEndpoint");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					sendEvent(getReactApplicationContext(), "disconnected_from_endpoint", endpointId);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when connected to a nearby advertiser 
	 */
	private void connectedToEndpoint(Endpoint endpoint) {
		String endpointId = endpoint.getId();

		mEstablishedConnections.put(endpointId, endpoint);

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.ConnectedToEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerConnectedToEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.ConnectedToEndpoint");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					sendEvent(getReactApplicationContext(), "connected_to_endpoint", endpointId);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when a connection to a nearby advertiser is initiated
	 */
	private void connectionInitiatedToEndpoint(Endpoint endpoint, final ConnectionInfo connectionInfo) {
		String endpointId = endpoint.getId();
		String authenticationToken = connectionInfo.getAuthenticationToken();
		String endpointName = connectionInfo.getEndpointName();
		Boolean incomingConnection = connectionInfo.isIncomingConnection();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.ConnectionInitiatedToEndpoint");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		bundle.putString("authenticationToken", authenticationToken);
		bundle.putString("endpointName", endpointName);
		bundle.putBoolean("incomingConnection", incomingConnection);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerConnectionInitiatedToEndpointHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.ConnectionInitiatedToEndpoint");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					String authenticationToken = intent.getStringExtra("authenticationToken");
					String endpointName = intent.getStringExtra("endpointName");
					Boolean incomingConnection = intent.getBooleanExtra("incomingConnection", false);

					WritableMap out = Arguments.createMap();
					out.putString("endpointId", endpointId);
					out.putString("authenticationToken", authenticationToken);
					out.putString("endpointName", endpointName);
					out.putBoolean("incomingConnection", incomingConnection);

					sendEvent(getReactApplicationContext(), "connection_initiated_to_endpoint", out);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when a nearby endpoint is lost
	 */
	private void onEndpointLost(String endpointId) {
		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.EndPointLost");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerEndpointLostHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.EndPointLost");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					sendEvent(getReactApplicationContext(), "endpoint_lost", endpointId);
				}
			}
		}, intentFilter);
	}

	/**
	 * Called when a remote endpoint is discovered.
     */
	private void onEndpointDiscovered(Endpoint endpoint) {
		// We found an advertiser!
		stopDiscovering();
		String endpointId = endpoint.getId();

		// Broadcast endpoint discovered
		Intent i = new Intent("com.googlenearbyconnection.nearbyconnection.EndPointDiscovered");
		Bundle bundle = new Bundle();
		bundle.putString("endpointId", endpointId);
		i.putExtras(bundle);

		final Activity activity = getCurrentActivity();
		activity.sendBroadcast(i);
	}

	private void registerEndpointDiscoveredHandler() {
		IntentFilter intentFilter = new IntentFilter("com.googlenearbyconnection.nearbyconnection.EndPointDiscovered");
		getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (getReactApplicationContext().hasActiveCatalystInstance()) {
					String endpointId = intent.getStringExtra("endpointId");
					sendEvent(getReactApplicationContext(), "endpoint_discovered", endpointId);
				}
			}
		}, intentFilter);
	}

	/**
	 * Sends a connection request to the endpoint. Either {@link #onConnectionInitiated(Endpoint,
	 * ConnectionInfo)} or {@link #onConnectionFailed(Endpoint)} will be called once we've found out
	 * if we successfully reached the device.
	 */
	@ReactMethod
	public void connectToEndpoint(final String endpointId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		//logV("Sending a connection request to endpoint " + endpoint);
		// Mark ourselves as connecting so we don't connect multiple times
		mIsConnecting = true;

		// Ask to connect
		clientSingleton
			.requestConnection(getName(), endpointId, mConnectionLifecycleCallback)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						mIsConnecting = false;

						endpointConnectionFailed(endpointId, -1);
					}
				}
			);
	}

	/** Disconnects from the given endpoint. */
	@ReactMethod
	public void disconnectFromEndpoint(String endpointId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		clientSingleton.disconnectFromEndpoint(endpointId);

		disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));
	}

	/** Send sound from the microphone and streaming it to all connected devices. */
	@ReactMethod
	public void openMicrophone(final String endpointId, final Promise promise) {
		final Activity activity = getCurrentActivity();

		permissionsCheck(activity, promise, Arrays.asList(getRequiredPermissions()), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
				try {
					ParcelFileDescriptor[] payloadPipe = ParcelFileDescriptor.createPipe();

					// Send the first half of the payload (the read side) to Nearby Connections.
					sendPayload(Payload.fromStream(payloadPipe[0]), endpointId);

					// Use the second half of the payload (the write side) in AudioRecorder.
					mRecorder = new AudioRecorder(payloadPipe[1]);
					mRecorder.start();
				}
				catch (IOException e) {
				}

				return null;
			}
		});
	}

	/** Starts streaming sound from an endpoint. */
	@ReactMethod
	private void startPlayingAudioStream(final String endpointId) {
		Payload payload = mReceivedPayloads.get(endpointId);

		if (payload.getType() != Payload.Type.STREAM) {
			Log.d(TAG, "startPlayingAudioStream failure.  Not a stream.");
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
							mAudioPlayers.remove(endpointId);
						}
					}
				);
			}
		};
		mAudioPlayers.put(endpointId, player);

		player.start();
	}

	/** Stops streaming sound from the microphone. */
	@ReactMethod
	private void closeMicrophone(String endpointId) {
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder = null;
		}
	}

	@ReactMethod
	private void stopPlayingAudioStream(String endpointId) {
		AudioPlayer player = mAudioPlayers.remove(endpointId);
		if (player == null) {
			return;
		}

		player.stop();
	}

	/** @return True if currently streaming from the microphone. */
	@ReactMethod
	private boolean isMicrophoneOpen() {
		return mRecorder != null && mRecorder.isRecording();
	}

	/**
	 * An optional hook to pool any permissions the app needs with the permissions ConnectionsActivity
	 * will request.
	 *
	 * @return All permissions required for the app to properly function.
	 */
	protected String[] getRequiredPermissions() {
		return REQUIRED_PERMISSIONS;
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

    private void permissionsCheck(final Activity activity, final Promise promise, final List<String> requiredPermissions, final Callable<Void> callback) {

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
                                promise.reject(E_PERMISSIONS_MISSING, "Required permission missing");
                                return true;
                            }
                        }

                        try {
                            callback.call();
                        } catch (Exception e) {
                            promise.reject(E_CALLBACK_ERROR, "Unknown error", e);
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
            promise.reject(E_CALLBACK_ERROR, "Unknown error", e);
        }
    }

	/**
	 * Sends a {@link Payload} to all currently connected endpoints.
	 *
	 * @param payload The data you want to send.
	 */
	protected void sendPayload(Payload payload, String endpointId) {
		final ConnectionsClient clientSingleton = getConnectionsClientSingleton();

		clientSingleton
			.sendPayload(endpointId, payload)
			.addOnFailureListener(
				new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
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
			//Log.d(TAG, "Missing context - cannot send event!");
		}
	}
	
	/** Represents a device we can talk to. */
	protected static class Endpoint {
		@NonNull private final String id;
		@NonNull private final String name;

		private Endpoint(@NonNull String id, @NonNull String name) {
		  this.id = id;
		  this.name = name;
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