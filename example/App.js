import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import {
	NativeEventEmitter,
	NativeModules,
	TouchableHighlight,
	Switch,
} from 'react-native';

import {
	COLOR, ThemeProvider,
	Button, ListItem, Subheader,
} from 'react-native-material-ui';

import Nearby, { ConnectionsStatusCodes, Strategy } from 'react-native-google-nearby-connection';

const uiTheme = {
    palette: {
        primaryColor: COLOR.green500,
    },
    toolbar: {
        container: {
            height: 50,
        },
    },
};

const localEndpointName = "NearbyConnectionExampleApp";
const serviceId = "my.local.connection.example.application";

export default class App extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			discovering: 'no',
			advertising: 'no',

			discovered_endpointIds: [],
			initiated_endpointIds: [],
			connected_endpointIds: [],
			microphone_endpointIds: [],
			playing_endpointIds: [],
			payload_endpointIds: {},
		};

		this.subscribed_events = [];
	}

	componentWillUnmount() {
		this.subscribed_events.forEach((event) => {
			event.remove();
		});
		this.subscribed_events = [];
	}

	componentDidMount() {
		// Discovery
		this.subscribed_events.push(
			Nearby.onDiscoveryStarting(() => {
				console.log("onDiscoveryStarting");
				this.setState({
					discovering: 'starting',
				})
			})
		);
		this.subscribed_events.push(
			Nearby.onDiscoveryStarted(() => {
				console.log("onDiscoveryStarted");
				this.setState({
					discovering: 'yes',
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onDiscoveryStartFailed((statusCode) => {
				console.log("onDiscoveryStartFailed");

				this.setState({
					discovering: (statusCode === ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING)? 'yes' : 'no',
				});
			})
		);

		// Advertising
		this.subscribed_events.push(
			Nearby.onAdvertisingStarting(() => {
				console.log("onAdvertisingStarting");
				this.setState({
					advertising: 'starting',
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onAdvertisingStarted(() => {
				console.log("onAdvertisingStarted");
				this.setState({
					advertising: 'yes',
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onAdvertisingStartFailed((statusCode) => {

				this.setState({
					advertising: (statusCode === ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING)? 'yes' : 'no',
				});
			})
		);

		// Payload
		this.subscribed_events.push(
			Nearby.onReceivePayload((result) => {
				console.log("onReceivePayload", result);
				let payload_endpointIds = {...this.state.payload_endpointIds};
				payload_endpointIds[result.endpointId] = result;

				this.setState({
					payload_endpointIds: payload_endpointIds,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onPayloadUpdate((result) => {
				console.log("onPayloadUpdate", result);
			})
		);

		// Connection
		this.subscribed_events.push(
			Nearby.onConnectionInitiatedToEndpoint(({endpointId,authenticationToken,endpointName,incomingConnection}) => {
				console.log("onConnectionInitiatedToEndpoint",endpointId,authenticationToken,endpointName,incomingConnection);
				if (this.state.initiated_endpointIds.indexOf(endpointId) != -1) {
					return;
				}

				const initiated_endpointIds = [endpointId, ...this.state.initiated_endpointIds];

				this.setState({
					initiated_endpointIds: initiated_endpointIds,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onConnectedToEndpoint((endpointId) => {
				console.log("onConnectedToEndpoint");
				if (this.state.connected_endpointIds.indexOf(endpointId) != -1) {
					return;
				}

				const connected_endpointIds = [endpointId, ...this.state.connected_endpointIds];
				const initiated_endpointIds = [...this.state.initiated_endpointIds];
				initiated_endpointIds.splice(initiated_endpointIds.indexOf(endpointId), 1);

				this.setState({
					connected_endpointIds: connected_endpointIds,
					initiated_endpointIds: initiated_endpointIds,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onEndpointConnectionFailed(({endpointId, statusCode}) => {
				console.log("onEndpointConnectionFailed", endpointId, statusCode);
				if (statusCode === ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
					return;
				}

				const initiated_endpointIds = [...this.state.initiated_endpointIds];
				initiated_endpointIds.splice(initiated_endpointIds.indexOf(endpointId), 1);

				this.setState({
					initiated_endpointIds: initiated_endpointIds,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onDisconnectedFromEndpoint((endpointId) => {
				console.log("onDisconnectedFromEndpoint");
				const connected_endpointIds = [...this.state.connected_endpointIds];
				connected_endpointIds.splice(connected_endpointIds.indexOf(endpointId), 1);

				this.setState({
					connected_endpointIds: connected_endpointIds,
				});
			})
		);

		// Discovery
		this.subscribed_events.push(
			Nearby.onEndpointDiscovered(({endpointName,serviceId,endpointId}) => {
				console.log("onEndpointDiscovered", endpointName,serviceId,endpointId);
				if (this.state.discovered_endpointIds.indexOf(endpointId) != -1) {
					return;
				}
				const discovered_endpointIds = [endpointId, ...this.state.discovered_endpointIds];

				this.setState({
					discovered_endpointIds: discovered_endpointIds,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onEndpointLost((endpointId) => {
				console.log("onEndpointLost");
				const discovered_endpointIds = [...this.state.discovered_endpointIds];
				discovered_endpointIds.splice(discovered_endpointIds.indexOf(endpointId), 1);

				this.setState({
					discovered_endpointIds: discovered_endpointIds,
				});
			})
		);
		
	}

	handleAcceptConnection = (endpointId) => () => {
		Nearby.acceptConnection(endpointId);
	}
	handleRejectConnection = (endpointId) => () => {
		Nearby.rejectConnection(endpointId);
	}

	handleConnectToEndpoint = (endpointId) => () => {
		Nearby.connectToEndpoint(localEndpointName, endpointId);
	}
	handleDisconnectFromEndpoint = (endpointId) => () => {
		console.log("CALLING disconnectFromEndpoint");
		Nearby.disconnectFromEndpoint(endpointId);
	}
	
	handleStartAdvertising = () => {
		Nearby.startAdvertising(localEndpointName, serviceId);
	}
	handleStopAdvertising = () => {
		Nearby.stopAdvertising(serviceId);
		this.setState({
			advertising: 'no'
		});
	}
	
	handleStartDiscovering = () => {
		Nearby.startDiscovering(serviceId);
	}
	handleStopDiscovering = () => {
		Nearby.stopDiscovering(serviceId);
		this.setState({
			discovering: 'no'
		});
	}

	handleOpenMicrophone = (endpointId) => () => {
		Nearby.openMicrophone(endpointId);
		const microphone_endpointIds = [endpointId, ...this.state.microphone_endpointIds];

		this.setState({
			microphone_endpointIds: microphone_endpointIds,
		});
	}
	handleCloseMicrophone = (endpointId) => () => {
		Nearby.closeMicrophone(endpointId);
		const microphone_endpointIds = [...this.state.microphone_endpointIds];
		microphone_endpointIds.splice(microphone_endpointIds.indexOf(endpointId), 1);

		this.setState({
			microphone_endpointIds: microphone_endpointIds,
		});
	}

	handleStartPlayingAudioStream = (endpointId) => () => {
		Nearby.startPlayingAudioStream(endpointId);
		const playing_endpointIds = [endpointId, ...this.state.playing_endpointIds];

		this.setState({
			playing_endpointIds: playing_endpointIds,
		});
	}
	handleStopPlayingAudioStream = (endpointId) => () => {
		Nearby.stopPlayingAudioStream(endpointId);
		const playing_endpointIds = [...this.state.playing_endpointIds];
		playing_endpointIds.splice(playing_endpointIds.indexOf(endpointId), 1);

		this.setState({
			playing_endpointIds: playing_endpointIds,
		});
	}

	render() {
		console.log(this.state);
		return (
			<ThemeProvider uiTheme={uiTheme}>
			<View style={styles.container}>
				<View style={{flexDirection:'row', alignItems: 'center', justifyContent: 'space-between', padding: 10}}>
					<Text>Visible to other devices?</Text>
					<View style={{}}><Switch value={((this.state.advertising !== 'no')? true : false)} onValueChange={((this.state.advertising === 'no')? this.handleStartAdvertising : this.handleStopAdvertising)} /></View>
				</View>
				{(() => {
					if (this.state.discovering !== 'starting') {
						return (
							<Button raised primary onPress={((this.state.discovering === 'no')? this.handleStartDiscovering : this.handleStopDiscovering)} text={((this.state.discovering === 'no')? "Find Devices" : "Stop Finding Devices")} />
						);
					}
					return (
						<Button raised primary text="Starting Discovery" />
					);
				})()}

				<Subheader text="Discovered Endpoints" />
				{(() => {
					return this.state.discovered_endpointIds.map((endpointId) => {
						return (
							<ListItem
								style={{container:{}}}
								key={"connect_to_"+endpointId}
								divider
								leftElement={
									<Text>{endpointId}</Text>
								}
								rightElement={
									<Button raised primary onPress={this.handleConnectToEndpoint(endpointId)} text="Connect" />
								}
									/>
						);
					});
				})()}

				<Subheader text="Initiated Endpoints" />
				{(() => {
					return this.state.initiated_endpointIds.map((endpointId) => {
						return (
							<ListItem
								style={{container:{}}}
								key={"initiated_to_"+endpointId}
								divider
								leftElement={
									<Text>{endpointId}</Text>
								}
								rightElement={
									<View style={{flexDirection:"row"}}>
										<Button raised primary onPress={this.handleAcceptConnection(endpointId)} text="Accept" /><Text></Text>
										<Button raised primary onPress={this.handleRejectConnection(endpointId)} text="Reject" />
									</View>
								}
									/>
						);
					});
				})()}

				<Subheader text="Connected Endpoints" />
				{(() => {
					return this.state.connected_endpointIds.map((endpointId) => {
						return (
							<ListItem
								style={{container:{height: 250}}}
								key={"initiated_to_"+endpointId}
								divider
								leftElement={
									<Text>{endpointId}</Text>
								}
								rightElement={
									<View style={{flexDirection: 'column'}}>
										{(() => {
											if (this.state.payload_endpointIds[endpointId]) {
												return (
													<Button raised secondary onPress={((this.state.playing_endpointIds.indexOf(endpointId) != -1)? this.handleStopPlayingAudioStream(endpointId) : this.handleStartPlayingAudioStream(endpointId))} text={((this.state.playing_endpointIds.indexOf(endpointId) != -1)? "Stop Audio" : "Start Audio")} />
												);
											}
										})()}
										<Button raised primary onPress={((this.state.microphone_endpointIds.indexOf(endpointId) != -1)? this.handleCloseMicrophone(endpointId) : this.handleOpenMicrophone(endpointId))} text={((this.state.microphone_endpointIds.indexOf(endpointId) != -1)? "Close Microphone" : "Open Microphone")} />
										<Text></Text>
										<Button raised primary onPress={this.handleDisconnectFromEndpoint(endpointId)} text="Disconnect" />
									</View>
								}
									/>
						);
					});
				})()}
			</View>
			</ThemeProvider>
		);
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		flexDirection: 'column',
		backgroundColor: '#fff',
		//alignItems: 'center',
		//justifyContent: 'center',
	},
	button: {
		borderColor: 'black',
		padding: 10,
		borderWidth: 1
	}
});
