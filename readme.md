# react-native-google-nearby-connection [![npm version](https://badge.fury.io/js/react-native-google-nearby-connection.svg)](https://badge.fury.io/js/react-native-google-nearby-connection)
React Native wrapper for Googles [Nearby Connection API](https://developers.google.com/nearby/connections/overview)

Download the React Native Nearby Connection demo app from the [Google Play Store (Android)](https://play.google.com/store/apps/details?id=com.butchmarshall.reactnative.nearby.connection)

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
android {
    ...
    compileSdkVersion 26
    buildToolsVersion "26.0.1"
    ...
    defaultConfig {
        ...
        minSdkVersion 16
        targetSdkVersion 26
        ...
        multiDexEnabled true
        ...
    }
...
dependencies {
    ...
    compile 'com.google.android.gms:play-services-nearby:11.8.0'
    compile project(':react-native-google-nearby-connection')
    compile 'com.android.support:appcompat-v7:25.3.1'
    compile 'com.android.support:multidex:1.0.1'
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
import NearbyConnection, {CommonStatusCodes, ConnectionsStatusCodes, Strategy, Payload, PayloadTransferUpdate} from 'react-native-google-nearby-connection';
```

Starting the discovery service

```javascript
NearbyConnection.startDiscovering(
    serviceId                // A unique identifier for the service
);
```

Stopping the discovery service


```javascript
NearbyConnection.stopDiscovering(serviceId);
```

Whether a service is currently discovering

```javascript
NearbyConnection.isDiscovering();
```

Connect to a discovered endpoint

```javascript
NearbyConnection.connectToEndpoint(
    serviceId,              // A unique identifier for the service
    endpointId              // ID of the endpoint to connect to
);
```

Disconnect from an endpoint

```javascript
NearbyConnection.disconnectFromEndpoint(
    serviceId,              // A unique identifier for the service
    endpointId              // ID of the endpoint we wish to disconnect from
);
```

Starting the advertising service

```javascript
NearbyConnection.startAdvertising(
    endpointName,           // This nodes endpoint name
    serviceId,              // A unique identifier for the service
    strategy                // The Strategy to be used when discovering or advertising to Nearby devices [See Strategy](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Strategy)
);
```

Stopping the advertising service

```javascript
NearbyConnection.stopAdvertising(
    serviceId                // A unique identifier for the service
);
```

Whether a service is currently advertising

```javascript
NearbyConnection.isAdvertising();
```

Accepting a connection from an endpoint

```javascript
NearbyConnection.acceptConnection(
    serviceId,               // A unique identifier for the service
    endpointId               // ID of the endpoint wishing to accept the connection from
);
```

Rejecting a connection from an endpoint

```javascript
NearbyConnection.rejectConnection(
    serviceId,               // A unique identifier for the service
    endpointId               // ID of the endpoint wishing to reject the connection from
);
```

Removes a payload (free memory)

```javascript
NearbyConnection.removePayload(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    payloadId                // Unique identifier of the payload
);
```

Open the microphone and broadcast audio to an endpoint

```javascript
NearbyConnection.openMicrophone(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to send the audio to
    metadata                 // String of metadata you wish to pass along with the stream
);
```

Stop broadcasting audio to an endpoint

```javascript
NearbyConnection.closeMicrophone(
    serviceId,               // A unique identifier for the service
    endpointId               // ID of the endpoint wishing to stop sending audio to
);
```

Start playing an audio stream from a received payload (Payload.STREAM)

```javascript
NearbyConnection.startPlayingAudioStream(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to start playing audio from
    payloadId                // Unique identifier of the payload
);
```

Stop playing an audio stream from a received payload (Payload.STREAM)

```javascript
NearbyConnection.stopPlayingAudioStream(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    payloadId                // Unique identifier of the payload
);
```

Send a file to a service endpoint (Payload.FILE)

```javascript
NearbyConnection.sendFile(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    uri,                     // Location of the file to send
    metadata                 // String of metadata you wish to pass along with the file
);
```

Save a file from a payload (Payload.FILE) to storage

```javascript
NearbyConnection.saveFile(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    payloadId,               // Unique identifier of the payload
).then({
    path,                    // Path where the file was saved on local filesystem
    originalFilename,        // The original name of the sent file
    metadata,                // Any metadata that was sent along with sendFile
}) => {
});
```

Send a bytes payload (Payload.BYTES)

```javascript
NearbyConnection.sendBytes(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    bytes                    // A string of bytes to send
);
```

Read payload \[Payload\.Type\.BYTES\] or out of band file \[Payload\.Type\.FILE\] or stream \[Payload\.Type\.STREAM\] information

```javascript
NearbyConnection.readBytes(
    serviceId,               // A unique identifier for the service
    endpointId,              // ID of the endpoint wishing to stop playing audio from
    payloadId                // Unique identifier of the payload
).then(({
    type,                    // The Payload.Type represented by this payload
    bytes,                   // [Payload.Type.BYTES] The bytes string that was sent
    payloadId,               // [Payload.Type.FILE or Payload.Type.STREAM] The payloadId of the payload this payload is describing
    filename,                // [Payload.Type.FILE] The name of the file being sent
    metadata,                // [Payload.Type.FILE] The metadata sent along with the file
    streamType,              // [Payload.Type.STREAM] The type of stream this is [audio or video]
}) => {
});
```


## Callbacks

Endpoint Discovery

```javascript
NearbyConnection.onDiscoveryStarting(({
    serviceId               // A unique identifier for the service
}) => {
    // Discovery services is starting
});

NearbyConnection.onDiscoveryStarted(({
    serviceId               // A unique identifier for the service
}) => {
    // Discovery services has started
});

NearbyConnection.onDiscoveryStartFailed(({
    serviceId               // A unique identifier for the service
    statusCode              // The status of the response [See CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
}) => {
    // Failed to start discovery service
});

// Note - Can take up to 3 min to time out
NearbyConnection.onEndpointLost(({
    endpointId,             // ID of the endpoint we lost
    endpointName,           // The name of the remote device we lost
    serviceId               // A unique identifier for the service
}) => {
    // Endpoint moved out of range or disconnected
});

NearbyConnection.onEndpointDiscovered(({
    endpointId,             // ID of the endpoint wishing to connect
    endpointName,           // The name of the remote device we're connecting to.
    serviceId               // A unique identifier for the service
}) => {
    // An endpoint has been discovered we can connect to
});
```

Endpoint Advertisement

```javascript
NearbyConnection.onAdvertisingStarting(({
    endpointName,            // The name of the service thats starting to advertise
    serviceId,               // A unique identifier for the service
}) => {
    // Advertising service is starting
});

NearbyConnection.onAdvertisingStarted(({
    endpointName,            // The name of the service thats started to advertise
    serviceId,               // A unique identifier for the service
}) => {
    // Advertising service has started
});

NearbyConnection.onAdvertisingStartFailed(({
    endpointName,            // The name of the service thats failed to start to advertising
    serviceId,               // A unique identifier for the service
    statusCode,              // The status of the response [See CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
}) => {
    // Failed to start advertising service
});
```

Connection negotiation

```javascript
NearbyConnection.onConnectionInitiatedToEndpoint(({
    endpointId,             // ID of the endpoint wishing to connect
    endpointName,           // The name of the remote device we're connecting to.
    authenticationToken,    // A small symmetrical token that has been given to both devices.
    serviceId,              // A unique identifier for the service
    incomingConnection      // True if the connection request was initated from a remote device.
}) => {
    // Connection has been initated
});

NearbyConnection.onConnectedToEndpoint(({
    endpointId,             // ID of the endpoint we connected to
    endpointName,           // The name of the service
    serviceId,              // A unique identifier for the service
}) => {
    // Succesful connection to an endpoint established
});

NearbyConnection.onEndpointConnectionFailed(({
    endpointId,             // ID of the endpoint we failed to connect to
    endpointName,           // The name of the service
    serviceId,              // A unique identifier for the service
    statusCode              // The status of the response [See CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
}) => {
    // Failed to connect to an endpoint
});

NearbyConnection.onDisconnectedFromEndpoint(({
    endpointId,             // ID of the endpoint we disconnected from
    endpointName,           // The name of the service
    serviceId,              // A unique identifier for the service
}) => {
    // Disconnected from an endpoint
});
```

Payload Status

```javascript
Nearby.onReceivePayload(({
    serviceId,              // A unique identifier for the service
    endpointId,             // ID of the endpoint we got the payload from
    payloadType,            // The type of this payload (File or a Stream) [See Payload](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload)
    payloadId               // Unique identifier of the payload
}) => {
    // Payload has been received
});

Nearby.onPayloadUpdate(({
    serviceId,              // A unique identifier for the service
    endpointId,             // ID of the endpoint we got the payload from
    bytesTransferred,       // Bytes transfered so far
    totalBytes,             // Total bytes to transfer
    payloadId,              // Unique identifier of the payload
    payloadStatus,          // [See PayloadTransferUpdate.Status](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadTransferUpdate.Status)
    payloadHashCode,        // ???
}) => {
    // Update on a previously received payload
});

Nearby.onSendPayloadFailed(({
    serviceId,              // A unique identifier for the service
    endpointId,             // ID of the endpoint wishing to connect
    statusCode              // The status of the response [See CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
}) => {
    // Failed to send a payload
});
```