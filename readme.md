# react-native-google-nearby-connection
React Native wrapper for Googles [Nearby Connection API](https://developers.google.com/nearby/connections/overview)

# Install

## Install package

```bash
npm i react-native-google-nearby-connection --save
```

### Android

- Make sure you are using Gradle `2.2.x` (project build.gradle)
- Add google-services

```gradle
buildscript {
    ...
    dependencies {
        ...
        classpath 'com.android.tools.build:gradle:2.2.3'
        classpath 'com.google.gms:google-services:3.1.2'
        ...
    }
    ...
}
```

- Add the following to your `build.gradle`'s repositories section. (project build.gradle)

```gradle
allprojects {
    repositories {
        ...
        maven { url "https://maven.google.com" }
        ...
    }
}
```

### app:build.gradle

Add project under `dependencies`

```
dependencies {
    ...
	compile 'com.google.android.gms:play-services-nearby:11.8.0'
    compile project(':react-native-google-nearby-connection')
    ...
}
```

### settings.gradle

Include project, so gradle knows where to find the project

```
include ':react-native-google-nearby-connection'
project(':react-native-google-nearby-connection').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-google-nearby-connection/android')
```


### MainApplication.java

We need to register our package

Add `import com.butchmarshall.reactnative.google.nearby.connection.NearbyConnectionPackage;` as an import statement and
`new NearbyConnectionPackage()` in `getPackages()`


# Usage

Import library

```javascript
import NearbyConnection from 'react-native-google-nearby-connection';
```

Starting the discovery service

```javascript
NearbyConnection.startDiscovering();
```

Stopping the discovery service


```javascript
NearbyConnection.stopDiscovering();
```

Starting the advertising service

```javascript
NearbyConnection.startAdvertising();
```

Stopping the advertising service

```javascript
NearbyConnection.stopAdvertising();
```

Open the microphone and broadcast audio to an endpoint

```javascript
NearbyConnection.openMicrophone(endpointId);
```

Stop broadcasting audio to an endpoint

```javascript
NearbyConnection.closeMicrophone(endpointId);
```

Start playing an audio stream from a received payload

```javascript
NearbyConnection.startPlayingAudioStream(endpointId);
```

Stop playing an audio stream from a received payload

```javascript
NearbyConnection.stopPlayingAudioStream(endpointId);
```

## Callbacks

Endpoint Discovery

```javascript
NearbyConnection.onDiscoveryStarting(() => {
	// Discovery services is starting
});

NearbyConnection.onDiscoveryStarted(() => {
	// Discovery services has started
});

NearbyConnection.onDiscoveryStartFailed(() => {
	// Failed to start discovery service
});

!!! BROKEN - I cannot figure out why this event never fires
NearbyConnection.onEndpointLost(() => {
	// Endpoint moved out of range or disconnected
});
```

Endpoint Advertisement

```javascript
NearbyConnection.onAdvertisingStarting(() => {
	// Advertising service is starting
});

NearbyConnection.onAdvertisingStarted(() => {
	// Advertising service has started
});

NearbyConnection.onAdvertisingStartFailed(() => {
	// Failed to start advertising service
});
```

Connection negotiation

```javascript
NearbyConnection.onConnectionInitiatedToEndpoint(({
	endpointId,             // ID of the endpoint wishing to connect
	authenticationToken,    // A small symmetrical token that has been given to both devices.
	endpointName,           // The name of the remote device we're connecting to.
	incomingConnection      // True if the connection request was initated from a remote device.
}) => {
	// Connection has been initated
});

NearbyConnection.onConnectedToEndpoint((endpointId) => {
	// Succesful connection to an endpoint established
});

NearbyConnection.onEndpointConnectionFailed(({
	endpointId,             // ID of the endpoint we failed to connect to
	statusCode              // The status of the response [See CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
}) => {
	// Failed to connect to an endpoint
});

NearbyConnection.onDisconnectedFromEndpoint((endpointId) => {
	// Disconnected from an endpoint
});
```

Payload Status

```javascript
Nearby.onReceivePayload(({
	endpointId,             // ID of the endpoint we got the payload from
	payloadType,            // The type of this payload (File or a Stream) [See Payload](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload)
	payloadId               // Unique identifier of the payload
}) => {
	// Payload has been received
});

Nearby.onPayloadUpdate(({
	bytesTransferred,       // Bytes transfered so far
	totalBytes,             // Total bytes to transfer
	payloadId,              // Unique identifier of the payload
	payloadStatus,          // [See PayloadTransferUpdate.Status](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadTransferUpdate.Status)
	payloadHashCode,        // ???
}) => {
	// Update on a previously received payload
});
```