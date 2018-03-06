/*

String statusCode = "";
switch (result.getStatus().getStatusCode()) {
	case ConnectionsStatusCodes.STATUS_OK:
		statusCode = "STATUS_OK";
		Log.d("NearbyConnectionModule", "ConnectionsStatusCodes.STATUS_OK");
		// We're connected! Can now start sending and receiving data.
		break;
	case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
		statusCode = "STATUS_CONNECTION_REJECTED";
		// The connection was rejected by one or both sides.
		Log.d("NearbyConnectionModule", "ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED");
		break;
	case ConnectionsStatusCodes.STATUS_ERROR:
		statusCode = "STATUS_ERROR";
		// The connection broke before it was able to be accepted.
		Log.d("NearbyConnectionModule", "ConnectionsStatusCodes.STATUS_ERROR");
		break;
}

*/

import {
	NativeEventEmitter,
	NativeModules,
} from 'react-native';

const NearbyEventEmitter = new NativeEventEmitter(NativeModules.NearbyConnection);

export const CommonStatusCodes = {
	"API_NOT_CONNECTED": 17,
	"CANCELED": 16,
	"DEVELOPER_ERROR": 10,
	"ERROR": 13,
	"INTERNAL_ERROR": 8,
	"INTERRUPTED": 14,
	"INVALID_ACCOUNT": 5,
	"NETWORK_ERROR": 7,
	"RESOLUTION_REQUIRED": 6,
	"SERVICE_DISABLED": 3,
	"SERVICE_VERSION_UPDATE_REQUIRED": 2,
	"SIGN_IN_REQUIRED": 4,
	"SUCCESS": 0,
	"SUCCESS_CACHE": 1,
	"TIMEOUT": 15,
};

export const ConnectionsStatusCodes = {
	"API_CONNECTION_FAILED_ALREADY_IN_USE": 8050,
	"MISSING_PERMISSION_ACCESS_COARSE_LOCATION": 8034,
	"MISSING_PERMISSION_ACCESS_WIFI_STATE": 8032,
	"MISSING_PERMISSION_BLUETOOTH": 8030,
	"MISSING_PERMISSION_BLUETOOTH_ADMIN": 8031,
	"MISSING_PERMISSION_CHANGE_WIFI_STATE": 8033,
	"MISSING_PERMISSION_RECORD_AUDIO": 8035,
	"MISSING_SETTING_LOCATION_MUST_BE_ON": 8025,
	"STATUS_ALREADY_ADVERTISING": 8001,
	"STATUS_ALREADY_CONNECTED_TO_ENDPOINT": 8003,
	"STATUS_ALREADY_DISCOVERING": 8002,
	"STATUS_ALREADY_HAVE_ACTIVE_STRATEGY": 8008,
	"STATUS_BLUETOOTH_ERROR": 8007,
	"STATUS_CONNECTION_REJECTED": 8004,
	"STATUS_ENDPOINT_IO_ERROR": 8012,
	"STATUS_ENDPOINT_UNKNOWN": 8011,
	"STATUS_ERROR": 13,
	"STATUS_NETWORK_NOT_CONNECTED": 8000,
	"STATUS_NOT_CONNECTED_TO_ENDPOINT": 8005,
	"STATUS_OK": 0,
	"STATUS_OUT_OF_ORDER_API_CALL": 8009,
	"STATUS_PAYLOAD_IO_ERROR": 8013,
};

export const Payload = {
	"BYTES": 1,
	"FILE": 2,
	"STREAM": 3,
};

export const PayloadTransferUpdate = {
	"FAILURE": 2,
	"IN_PROGRESS": 3,
	"SUCCESS": 1,
};

export const Strategy = {
	"P2P_CLUSTER": 0,
	"P2P_STAR": 1,
};

class NearbyConnection {
	// Get all the endpoints
	static endpoints() {
		return NativeModules.NearbyConnection.endpoints();
	}

	// Get all payloads
	static payloads() {
		return NativeModules.NearbyConnection.payloads();
	}

	// Save a file from a payload to a location
	static removePayload(serviceId, endpointId, payloadId) {
		return NativeModules.NearbyConnection.removePayload(serviceId, endpointId, String(payloadId));
	}

	// Send a file payload
	static sendFile(serviceId, endpointId, uri, metadata = "") {
		return NativeModules.NearbyConnection.sendFile(serviceId, endpointId, uri, metadata);
	}
	// Save a file from a payload
	static saveFile(serviceId, endpointId, payloadId) {
		return NativeModules.NearbyConnection.saveFile(serviceId, endpointId, String(payloadId));
	}

	// Send out a bytes payload
	static sendBytes(serviceId, endpointId, bytes) {
		return NativeModules.NearbyConnection.sendBytes(serviceId, endpointId, String(bytes));
	}
	// Read out bytes
	static readBytes(serviceId, endpointId, payloadId) {
		return NativeModules.NearbyConnection.readBytes(serviceId, endpointId, String(payloadId));
	}

	// Open the microphone
	static openMicrophone(serviceId, endpointId, metadata = "") {
		return NativeModules.NearbyConnection.openMicrophone(serviceId, endpointId, metadata);
	}
	static closeMicrophone(serviceId, endpointId) {
		return NativeModules.NearbyConnection.closeMicrophone(serviceId, endpointId);
	}

	// Play a audio payload
	static startPlayingAudioStream(serviceId, endpointId, payloadId) {
		return NativeModules.NearbyConnection.startPlayingAudioStream(serviceId, endpointId, String(payloadId));
	}
	// Stop playing an audio payload
	static stopPlayingAudioStream(serviceId, endpointId, payloadId) {
		return NativeModules.NearbyConnection.stopPlayingAudioStream(serviceId, endpointId, String(payloadId));
	}

	// Start/Stop Advertise
	static startAdvertising(localEndpointName, serviceId, strategy = Strategy.P2P_CLUSTER) {
		return NativeModules.NearbyConnection.startAdvertising(localEndpointName, serviceId, strategy);
	}
	static stopAdvertising(serviceId) {
		return NativeModules.NearbyConnection.stopAdvertising(serviceId);
	}
	static isAdvertising() {
		return NativeModules.NearbyConnection.isAdvertising();
	}

	// Start/Stop Discover
	static startDiscovering(serviceId, strategy = Strategy.P2P_CLUSTER) {
		return NativeModules.NearbyConnection.startDiscovering(serviceId, strategy);
	}
	static stopDiscovering(serviceId) {
		return NativeModules.NearbyConnection.stopDiscovering(serviceId);
	}
	static isDiscovering() {
		return NativeModules.NearbyConnection.isDiscovering();
	}

	// Accept or Reject
	static acceptConnection(serviceId, endpointId) {
		return NativeModules.NearbyConnection.acceptConnection(serviceId, endpointId);
	}
	static rejectConnection(serviceId, endpointId) {
		return NativeModules.NearbyConnection.rejectConnection(serviceId, endpointId);
	}

	// Connect or Disconnect
	static connectToEndpoint(serviceId, endpointId) {
		return NativeModules.NearbyConnection.connectToEndpoint(serviceId, endpointId);
	}
	static disconnectFromEndpoint(serviceId, endpointId) {
		return NativeModules.NearbyConnection.disconnectFromEndpoint(serviceId, endpointId);
	}
	
	// ------------------------------------------------------------------------
	// Callbacks
	// ------------------------------------------------------------------------
	static onDiscoveryStarting(listener) {
		return NearbyEventEmitter.addListener('discovery_starting', listener);
	}
	static onDiscoveryStarted(listener) {
		return NearbyEventEmitter.addListener('discovery_started', listener);
	}
	static onDiscoveryStartFailed(listener) {
		return NearbyEventEmitter.addListener('discovery_start_failed', listener);
	}
	static onAdvertisingStarting(listener) {
		return NearbyEventEmitter.addListener('advertising_starting', listener);
	}
	static onAdvertisingStarted(listener) {
		return NearbyEventEmitter.addListener('advertising_started', listener);
	}
	static onAdvertisingStartFailed(listener) {
		return NearbyEventEmitter.addListener('advertising_start_failed', listener);
	}

	// Connection
	static onConnectionInitiatedToEndpoint(listener) {
		return NearbyEventEmitter.addListener('connection_initiated_to_endpoint', listener);
	}
	static onConnectedToEndpoint(listener) {
		return NearbyEventEmitter.addListener('connected_to_endpoint', listener);
	}
	static onEndpointConnectionFailed(listener) {
		return NearbyEventEmitter.addListener('endpoint_connection_failed', listener);
	}
	static onDisconnectedFromEndpoint(listener) {
		return NearbyEventEmitter.addListener('disconnected_from_endpoint', listener);
	}

	// Discovery
	static onEndpointDiscovered(listener) {
		return NearbyEventEmitter.addListener('endpoint_discovered', listener);
	}
	static onEndpointLost(listener) {
		return NearbyEventEmitter.addListener('endpoint_lost', listener);
	}

	// Payload
	static onReceivePayload(listener) {
		return NearbyEventEmitter.addListener('receive_payload', listener);
	}
	static onPayloadTransferUpdate(listener) {
		return NearbyEventEmitter.addListener('payload_transfer_update', listener);
	}
	static onSendPayloadFailed(listener) {
		return NearbyEventEmitter.addListener('send_payload_failed', listener);
	}
}

export default NearbyConnection;