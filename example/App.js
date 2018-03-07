import React from 'react';

import {
	NativeEventEmitter,
	NativeModules,
	TouchableHighlight,
	PermissionsAndroid,
	StyleSheet,
	Switch,
	View,
	Text as Native_Text,
	ToastAndroid,
} from 'react-native';

import Prompt from 'react-native-prompt';
import ImagePicker from 'react-native-image-picker';

import RandomWords from 'random-words';
import Immutable from 'immutable';

import {
	Container,
	Content,
	Header,
	Title, Left, Body, Right,
	Card, CardItem,
	Button, Text, Icon,
} from 'native-base';

import Nearby, { ConnectionsStatusCodes, Strategy, Payload as Nearby_Payload, PayloadTransferUpdate } from 'react-native-google-nearby-connection';

import DiscoveringList from './src/components/discovering_list';
import AdvertisingList from './src/components/advertising_list';

const Service = new Immutable.Record({
	serviceId: undefined,
	endpointName: undefined,
	advertising: 'no',
	discovering: 'no',
	endpoints: Immutable.List([]),
});


const Endpoint = new Immutable.Record({
	id: undefined,
	name: undefined,
	serviceId: undefined,
	incomingConnection: false,
	authenticationToken: undefined,
	state: 'disconnected',
	microphone_open: false,
	payloads: Immutable.Map({}),
});

const Payload = new Immutable.Record({
	serviceId: undefined,
	endpointId: undefined,
	payloadType: undefined,
	payloadId: undefined,
	payloadStatus: undefined,
	totalBytes: undefined,
	bytesTransferred: undefined,
	payloadHashCode: undefined,
	data: undefined,
	playing: false
});

export default class App extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			showCreateService: false,
			showDiscoverService: false,
			advertising: Immutable.List([]),
			discovering: Immutable.List([]),
		};

		this.subscribed_events = [];
	}

	shouldComponentUpdate(nextProps, nextState) {
		if (nextState.showCreateService != this.state.showCreateService) {
			console.log("shouldComponentUpdate A");
			return true;
		}
		if (nextState.showDiscoverService != this.state.showDiscoverService) {
			console.log("shouldComponentUpdate B");
			return true;
		}
		if (nextState.advertising != this.state.advertising) {
			console.log("shouldComponentUpdate C");
			return true;
		}
		if (nextState.discovering != this.state.discovering) {
			console.log("shouldComponentUpdate D");
			return true;
		}

		return false;
	}

	componentWillMount() {
		Nearby.endpoints().then(
			(endpoints) => {
				console.log("endpoints", endpoints);
			}
		);
	}

	componentWillUnmount() {
		this.subscribed_events.forEach((event) => {
			event.remove();
		});
		this.subscribed_events = [];
	}

	componentDidMount() {
		PermissionsAndroid.requestMultiple([
			PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE,
			PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
		]);

		// Discovery
		this.subscribed_events.push(
			Nearby.onDiscoveryStarting(({serviceId}) => {
				ToastAndroid.showWithGravity("onDiscoveryStarting("+serviceId+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						service = service.set("discovering", "starting");
					}

					return service;
				});

				this.setState({
					discovering: discovering,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onDiscoveryStarted(({serviceId}) => {
				ToastAndroid.showWithGravity("onDiscoveryStarted("+serviceId+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						service = service.set("discovering", "yes");
					}

					return service;
				});

				this.setState({
					discovering: discovering,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onDiscoveryStartFailed(({serviceId,statusCode}) => {
				ToastAndroid.showWithGravity("onDiscoveryStartFailed("+serviceId+","+statusCode+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						service = service.set("discovering", "no");
					}

					return service;
				});

				this.setState({
					discovering: discovering,
				});
			})
		);

		// Advertising
		this.subscribed_events.push(
			Nearby.onAdvertisingStarting(({endpointName,serviceId}) => {
				ToastAndroid.showWithGravity("onAdvertisingStarting("+endpointName+","+serviceId+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId && service.endpointName == endpointName) {
						service = service.set("advertising", "starting");
					}

					return service;
				});

				this.setState({
					advertising: advertising,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onAdvertisingStarted(({endpointName, serviceId}) => {
				ToastAndroid.showWithGravity("onAdvertisingStarted("+endpointName+","+serviceId+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId && service.endpointName == endpointName) {
						service = service.set("advertising", "yes");
					}

					return service;
				});

				this.setState({
					advertising: advertising,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onAdvertisingStartFailed(({endpointName, serviceId, statusCode}) => {
				ToastAndroid.showWithGravity("onAdvertisingStartFailed("+endpointName+","+serviceId+","+statusCode+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId && service.endpointName == endpointName) {
						service = service.set("advertising", "no");
					}

					return service;
				});

				this.setState({
					advertising: advertising,
				});
			})
		);

		// Payload
		this.subscribed_events.push(
			Nearby.onReceivePayload(({serviceId,endpointId,payloadType,payloadId}) => {
				console.log("onReceivePayload("+serviceId+","+endpointId+","+payloadType+","+payloadId+")");

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								const payloads = endpoint.get("payloads").set(payloadId,new Payload({payloadType,payloadId,playing:false}));
								endpoint = endpoint.set("payloads",payloads);
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});
				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								const payloads = endpoint.get("payloads").set(payloadId,new Payload({payloadType,payloadId,playing:false}));
								endpoint = endpoint.set("payloads",payloads);
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onPayloadTransferUpdate((result) => {
				const {
					serviceId,
					endpointId,
					payloadId,
				} = result;
				console.log("onPayloadTransferUpdate("+JSON.stringify(result)+")");

				let discovering = this.state.discovering;
				discovering.forEach((service, discovering_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										//if (payload.payloadType === Payload.STREAM) {
											delete result.bytesTransferred;
											delete result.payloadHashCode;
										//}

										for(var key in result) {
											payload = payload.set(key, result[key]);
										}

										// Bytes should be read immediately
										if (payload.payloadType == Nearby_Payload.BYTES && payload.payloadStatus == PayloadTransferUpdate.SUCCESS) {
											this.handleReadBytes(endpoint, payload);
										}
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						discovering = discovering.set(discovering_index, service)
					}
				});

				let advertising = this.state.advertising;
				advertising.forEach((service, advertising_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										//if (payload.payloadType === Payload.STREAM) {
											delete result.bytesTransferred;
											delete result.payloadHashCode;
										//}

										for(var key in result) {
											payload = payload.set(key, result[key]);
										}

										// Bytes should be read immediately
										if (payload.payloadType == Nearby_Payload.BYTES && payload.payloadStatus == PayloadTransferUpdate.SUCCESS) {
											this.handleReadBytes(endpoint, payload);
										}
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						advertising = advertising.set(advertising_index, service)
					}
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			})
		);

		// Connection
		this.subscribed_events.push(
			Nearby.onConnectionInitiatedToEndpoint(({serviceId,endpointId,authenticationToken,endpointName,incomingConnection}) => {
				ToastAndroid.showWithGravity("onConnectionInitiatedToEndpoint("+serviceId+","+endpointId+","+authenticationToken+","+endpointName+","+incomingConnection+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				var newEndpoint = new Endpoint({
					id: endpointId,
					name: endpointName,
					serviceId: serviceId,
					authenticationToken: authenticationToken,
					incomingConnection: incomingConnection,
				});

				if (incomingConnection) {
					const advertising = this.state.advertising.map((service) => {
						if (service.serviceId == serviceId && service.endpointName == endpointName) {
							service = service.set("endpoints", service.endpoints.push(newEndpoint));
						}

						return service;
					});

					this.setState({
						advertising: advertising,
					});
				}
				else {
					Nearby.acceptConnection(serviceId, endpointId); // We initated this! ACCEPT IT
					const discovering = this.state.discovering.map((service) => {
						if (service.serviceId == serviceId) {
							const endpoints = service.endpoints.map((endpoint) => {
								if (endpoint.id === endpointId) {
									newEndpoint = newEndpoint.set("state", "waiting");
									endpoint = newEndpoint;
								}

								return endpoint;
							})

							service = service.set("endpoints", endpoints);
						}

						return service;
					});

					this.setState({
						discovering: discovering,
					});
				}
			})
		);
		this.subscribed_events.push(
			Nearby.onConnectedToEndpoint(({endpointId,serviceId,endpointName}) => {
				ToastAndroid.showWithGravity("onConnectedToEndpoint("+serviceId+","+endpointId+","+endpointName+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								endpoint = endpoint.set("state","connected");
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});
				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId && service.endpointName == endpointName) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								endpoint = endpoint.set("state","connected");
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onEndpointConnectionFailed(({endpointId, serviceId, endpointName, statusCode}) => {
				ToastAndroid.showWithGravity("onEndpointConnectionFailed("+serviceId+","+endpointId+","+endpointName+","+statusCode+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								endpoint = endpoint.set("state", "disconnected");
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});
				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId && service.endpointName == endpointName) {
						const endpoints = service.endpoints.filter((endpoint) => {
							if (endpoint.id === endpointId) {
								return false;
							}

							return true;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			})
		);
		this.subscribed_events.push(
			Nearby.onDisconnectedFromEndpoint(({endpointId,serviceId,endpointName}) => {
				ToastAndroid.showWithGravity("onDisconnectedFromEndpoint("+serviceId+","+endpointId+","+endpointName+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								endpoint = endpoint.set("state", "disconnected");
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});
				const advertising = this.state.advertising.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.filter((endpoint) => {
							if (endpoint.id === endpointId) {
								return false;
							}

							return true;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			})
		);

		// Discovery
		this.subscribed_events.push(
			Nearby.onEndpointDiscovered(({endpointName,serviceId,endpointId}) => {
				ToastAndroid.showWithGravity("onEndpointDiscovered("+serviceId+","+endpointId+","+endpointName+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				var endpoint = new Endpoint({
					id: endpointId,
					name: endpointName,
					serviceId: serviceId,
				});

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						let found = false;
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId) {
								endpoint = endpoint.set("state", "disconnected");
								found = true;
							}

							return endpoint;
						});
						if (!found) {
							endpoints = endpoints.push(endpoint);
							
						}
						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
				});
				Nearby.endpoints().then(
					(endpoints) => {
						console.log("endpoints", endpoints);
					}
				);
			})
		);
		this.subscribed_events.push(
			Nearby.onEndpointLost(({endpointName,serviceId,endpointId}) => {
				ToastAndroid.showWithGravity("onEndpointLost("+serviceId+","+endpointId+","+endpointName+")", ToastAndroid.SHORT, ToastAndroid.CENTER);

				const discovering = this.state.discovering.map((service) => {
					if (service.serviceId == serviceId) {
						const endpoints = service.endpoints.map((endpoint) => {
							if (endpoint.id === endpointId && endpoint.state !== "connected") {
								endpoint = endpoint.set("state", "lost");
							}

							return endpoint;
						});

						service = service.set("endpoints", endpoints);
					}

					return service;
				});

				this.setState({
					discovering: discovering,
				});
			})
		);
		
	}

	handleAcceptConnection = (serviceId, endpoint) => {
		Nearby.acceptConnection(serviceId, endpoint.id);
	}
	handleRejectConnection = (serviceId, endpoint) => {
		Nearby.rejectConnection(serviceId, endpoint.id);
	}

	handleConnectToEndpoint = (serviceId, endpoint) => {
		Nearby.connectToEndpoint(serviceId, endpoint.id);
	}
	handleDisconnectFromEndpoint = (serviceId, endpoint) => {
		//console.log("handleDisconnectFromEndpoint", serviceId, endpoint.toJS());
		Nearby.disconnectFromEndpoint(serviceId, endpoint.id);
	}

	handleStartAdvertising = (service) => {
		Nearby.startAdvertising(service.endpointName, service.serviceId, Strategy.P2P_STAR);
	}
	handleStopAdvertising = (service) => {
		const serviceId = service.serviceId;
		const endpointName = service.endpointName;

		Nearby.stopAdvertising(serviceId);

		const advertising = this.state.advertising.map((service) => {
			if (service.serviceId == serviceId) {
				service = service.set("advertising", "no");
			}

			return service;
		});

		this.setState({
			advertising: advertising,
		});
	}

	handleStartDiscovering = (service) => {
		Nearby.startDiscovering(service.serviceId, Strategy.P2P_STAR);
	}
	handleStopDiscovering = (service) => {
		//console.log("handleStopDiscovering", service.toJS());
		const serviceId = service.serviceId;

		Nearby.stopDiscovering(serviceId);
		const discovering = this.state.discovering.map((service) => {
			if (service.serviceId == serviceId) {
				service = service.set("discovering", "no");
			}

			return service;
		});

		this.setState({
			discovering: discovering,
		});
	}

	handleOpenMicrophone = (endpoint) => {
		const endpointId = endpoint.id;
		const serviceId = endpoint.serviceId;

		Nearby.openMicrophone(serviceId, endpointId);

		const discovering = this.state.discovering.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						endpoint = endpoint.set("microphone_open", true);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});
		const advertising = this.state.advertising.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						endpoint = endpoint.set("microphone_open", true);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});

		this.setState({
			discovering: discovering,
			advertising: advertising,
		});
	}
	handleCloseMicrophone = (endpoint) => {
		const endpointId = endpoint.id;
		const serviceId = endpoint.serviceId;

		Nearby.closeMicrophone(serviceId, endpointId);

		const discovering = this.state.discovering.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						endpoint = endpoint.set("microphone_open", false);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});
		const advertising = this.state.advertising.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						endpoint = endpoint.set("microphone_open", false);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});

		this.setState({
			discovering: discovering,
			advertising: advertising,
		});
	}

	handleSendFile = (endpoint) => {
		ImagePicker.showImagePicker({}, (response) => {
			if (response.didCancel) {
				console.log('User cancelled image picker');
			}else if (response.error) {
				console.log('ImagePicker Error: ', response.error);
			}
			else if (response.customButton) {
				console.log('User tapped custom button: ', response.customButton);
			}
			else {
				Nearby.sendFile(endpoint.serviceId, endpoint.id, response.uri);
			}
		});
	}

	handleStartPlayingAudioStream = (endpoint, payload) => {
		const serviceId = endpoint.serviceId,
		endpointId = endpoint.id,
		payloadId = payload.payloadId;

		Nearby.startPlayingAudioStream(serviceId, endpointId, payloadId);

		const discovering = this.state.discovering.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						const payloads = endpoint.get("payloads").set(payloadId,payload.set("playing", true));
						endpoint = endpoint.set("payloads",payloads);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});
		const advertising = this.state.advertising.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						const payloads = endpoint.get("payloads").set(payloadId,payload.set("playing", true));
						endpoint = endpoint.set("payloads",payloads);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});

		this.setState({
			discovering: discovering,
			advertising: advertising,
		});
	}
	handleStopPlayingAudioStream = (endpoint, payload) => {
		const serviceId = endpoint.serviceId,
		endpointId = endpoint.id,
		payloadId = payload.payloadId;

		Nearby.stopPlayingAudioStream(serviceId, endpointId, payloadId);

		const discovering = this.state.discovering.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						const payloads = endpoint.get("payloads").set(payloadId,payload.set("playing", false));
						endpoint = endpoint.set("payloads",payloads);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});
		const advertising = this.state.advertising.map((service) => {
			if (service.serviceId == serviceId) {
				const endpoints = service.endpoints.map((endpoint) => {
					if (endpoint.id === endpointId) {
						const payloads = endpoint.get("payloads").set(payloadId,payload.set("playing", false));
						endpoint = endpoint.set("payloads",payloads);
					}

					return endpoint;
				});

				service = service.set("endpoints", endpoints);
			}

			return service;
		});

		this.setState({
			discovering: discovering,
			advertising: advertising,
		});
	}
	handleSaveFile = (endpoint, payload) => {
		const serviceId = endpoint.serviceId,
		endpointId = endpoint.id,
		payloadId = payload.payloadId;

		console.log("handleSaveFile", endpoint.toJS(), payload);

		Nearby.saveFile(serviceId, endpointId, payloadId).then(
			(data) => {
				console.log("saveFile result:", data);
				ToastAndroid.showWithGravity(JSON.stringify(data), ToastAndroid.SHORT, ToastAndroid.CENTER);

				let discovering = this.state.discovering;
				discovering.forEach((service, discovering_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										payload = payload.set("data", data);
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						discovering = discovering.set(discovering_index, service)
					}
				});

				let advertising = this.state.advertising;
				advertising.forEach((service, advertising_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										payload = payload.set("data", data);
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						advertising = advertising.set(advertising_index, service)
					}
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			}
		);
	}
	handleReadBytes = (endpoint, payload) => {
		const serviceId = endpoint.serviceId,
		endpointId = endpoint.id,
		payloadId = payload.payloadId;

		console.log("handleReadBytes", endpoint.toJS(), payload);
		Nearby.readBytes(serviceId, endpointId, payloadId).then(
			(data) => {
				console.log("readBytes result: ", data);
				ToastAndroid.showWithGravity(JSON.stringify(data), ToastAndroid.SHORT, ToastAndroid.CENTER);

				let discovering = this.state.discovering;
				discovering.forEach((service, discovering_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										payload = payload.set("data", data);
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						discovering = discovering.set(discovering_index, service)
					}
				});

				let advertising = this.state.advertising;
				advertising.forEach((service, advertising_index) => {
					if (service.serviceId == serviceId) {
						let endpoints = service.endpoints;

						endpoints.forEach((endpoint, endpoint_index) => {
							if (endpoint.id === endpointId) {
								let payloads = endpoint.get("payloads");

								payloads.forEach((payload, k) => {
									if (k === payloadId) {
										payload = payload.set("data", data);
									}
									payloads = payloads.set(k, payload);
								});
								endpoint = endpoint.set("payloads",payloads);

								endpoints = endpoints.set(endpoint_index, endpoint);
							}
						});

						service = service.set("endpoints", endpoints);

						advertising = advertising.set(advertising_index, service)
					}
				});

				this.setState({
					discovering: discovering,
					advertising: advertising,
				});
			}
		);
	}
	handleSendBytes = (endpoint, bytes) => {
		console.log("handleSendBytes", endpoint.toJS(), bytes);
		Nearby.sendBytes(endpoint.serviceId, endpoint.id, bytes);
	}

	handleAddService = () => {
		this.setState({
			showCreateService: true
		})
	}
	handleCancelCreateService = () => {
		this.setState({
			showCreateService: false
		});
	}
	handleSubmitCreateService = (value) => {
		const serviceId = value,
		endpointName = RandomWords(3).map((word) => {
			return (word.charAt(0).toUpperCase() + word.slice(1));
		}).join('');

		this.setState({
			advertising: this.state.advertising.push(new Service({
				serviceId: serviceId,
				endpointName: endpointName,
			})),
			showCreateService: false
		});
	}

	handleDiscoverService = () => {
		this.setState({
			showDiscoverService: true
		})
	}
	handleCancelDiscoverService = () => {
		this.setState({
			showDiscoverService: false
		});
	}
	handleSubmitDiscoverService = (value) => {
		const serviceId = value;
		this.setState({
			discovering: this.state.discovering.push(new Service({
				serviceId: serviceId,
			})),
			showDiscoverService: false
		});
	}

	render() {
		console.log("--render!");
		return (
			<Container>
				<Prompt
						title="Create a Service"
						placeholder="Service ID"
						defaultValue=""
						visible={ this.state.showCreateService }
						onCancel={this.handleCancelCreateService}
						onSubmit={this.handleSubmitCreateService} />
				<Prompt
						title="Discover a Service"
						placeholder="Service ID"
						defaultValue=""
						visible={ this.state.showDiscoverService }
						onCancel={this.handleCancelDiscoverService}
						onSubmit={this.handleSubmitDiscoverService} />
				<Header>
					<Body>
						<Title>Advertising</Title>
					</Body>
					<Right>
						<Button transparent onPress={this.handleAddService}>
							<Icon name="add" style={{color: 'white'}} />
						</Button>
					</Right>
				</Header>
				<Content>
				<AdvertisingList items={this.state.advertising}
					onStartAdvertising={this.handleStartAdvertising}
					onStopAdvertising={this.handleStopAdvertising}
					onDisconnectFromEndpoint={this.handleDisconnectFromEndpoint}
					onAcceptConnection={this.handleAcceptConnection}
					onRejectConnection={this.handleRejectConnection}

					onCloseMicrophone={this.handleCloseMicrophone}
					onOpenMicrophone={this.handleOpenMicrophone}

					onSendFile={this.handleSendFile}

					onStopPlayingAudioStream={this.handleStopPlayingAudioStream}
					onStartPlayingAudioStream={this.handleStartPlayingAudioStream}
					onSaveFile={this.handleSaveFile}
					onReadBytes={this.handleReadBytes}
					onSendBytes={this.handleSendBytes}
						/>

				<Header>
					<Body>
						<Title>Discovery</Title>
					</Body>
					<Right>
						<Button transparent onPress={this.handleDiscoverService}>
							<Icon name="add" style={{color: 'white'}} />
						</Button>
					</Right>
				</Header>
				<DiscoveringList items={this.state.discovering}
					onStartDiscovering={this.handleStartDiscovering}
					onStopDiscovering={this.handleStopDiscovering}
					onDisconnectFromEndpoint={this.handleDisconnectFromEndpoint}
					onConnectToEndpoint={this.handleConnectToEndpoint}

					onCloseMicrophone={this.handleCloseMicrophone}
					onOpenMicrophone={this.handleOpenMicrophone}

					onSendFile={this.handleSendFile}

					onStopPlayingAudioStream={this.handleStopPlayingAudioStream}
					onStartPlayingAudioStream={this.handleStartPlayingAudioStream}
					onSaveFile={this.handleSaveFile}
					onReadBytes={this.handleReadBytes}
					onSendBytes={this.handleSendBytes}
						/>

				</Content>
			</Container>
		);
	}
}