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

class NearbyConnection {
	// Open the microphone
	static openMicrophone(endpointId) {
		NativeModules.NearbyConnection.openMicrophone(endpointId);
	}
	static closeMicrophone(endpointId) {
		NativeModules.NearbyConnection.closeMicrophone(endpointId);
	}
	
	static startPlayingAudioStream(endpointId) {
		NativeModules.NearbyConnection.startPlayingAudioStream(endpointId);
	}
	static stopPlayingAudioStream(endpointId) {
		NativeModules.NearbyConnection.stopPlayingAudioStream(endpointId);
	}

	// Start/Stop Advertise
	static startAdvertising() {
		NativeModules.NearbyConnection.startAdvertising();
	}
	static stopAdvertising() {
		NativeModules.NearbyConnection.stopAdvertising();
	}
	static isAdvertising() {
		NativeModules.NearbyConnection.isAdvertising();
	}

	// Start/Stop Discover
	static startDiscovering() {
		NativeModules.NearbyConnection.startDiscovering();
	}
	static stopDiscovering() {
		NativeModules.NearbyConnection.stopDiscovering();
	}
	static isDiscovering() {
		NativeModules.NearbyConnection.isDiscovering();
	}

	// Accept or Reject
	static acceptConnection(endpointId) {
		NativeModules.NearbyConnection.acceptConnection(endpointId);
	}
	static rejectConnection(endpointId) {
		NativeModules.NearbyConnection.rejectConnection(endpointId);
	}

	// Connect or Disconnect
	static connectToEndpoint(endpointId) {
		NativeModules.NearbyConnection.connectToEndpoint(endpointId);
	}
	static disconnectFromEndpoint(endpointId) {
		NativeModules.NearbyConnection.disconnectFromEndpoint(endpointId);
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
	static onPayloadUpdate(listener) {
		return NearbyEventEmitter.addListener('payload_update', listener);
	}
}

export default NearbyConnection;