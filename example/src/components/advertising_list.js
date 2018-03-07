import React from 'react';

import {
	TouchableHighlight,
	StyleSheet,
	Switch,
	View,
	Text as Native_Text,
} from 'react-native';

import {
	Container,
	Content,
	Header,
	Title, Left, Body, Right,
	Card, CardItem,
	Button, Text, Icon,
} from 'native-base';

import AudioRecorder from './audio_recorder';
import EndpointPayloads from './endpoint_payloads';
import SendFile from './send_file';
import SendMessage from './send_message';

const defaultFunc = () => {};

const AdvertisingList = (props) => {
	const {
		items = [],
		onStartAdvertising = defaultFunc,
		onStopAdvertising = defaultFunc,
		onDisconnectFromEndpoint = defaultFunc,
		onAcceptConnection = defaultFunc,
		onRejectConnection = defaultFunc,

		onCloseMicrophone = defaultFunc,
		onOpenMicrophone = defaultFunc,

		onSendFile = defaultFunc,

		onStopPlayingAudioStream = defaultFunc,
		onStartPlayingAudioStream = defaultFunc,
		onSaveFile = defaultFunc,

		onReadBytes = defaultFunc,
		onSendBytes = defaultFunc,
	} = props;

	const handleStartAdvertising = (service) => () => {
		onStartAdvertising(service);
	};
	const handleStopAdvertising = (service) => () => {
		onStopAdvertising(service);
	};
	const handleDisconnectFromEndpoint = (serviceId, endpoint) => () => {
		onDisconnectFromEndpoint(serviceId, endpoint);
	};
	const handleAcceptConnection = (serviceId, endpoint) => () => {
		onAcceptConnection(serviceId, endpoint);
	};
	const handleRejectConnection = (serviceId, endpoint) => () => {
		onRejectConnection(serviceId, endpoint);
	};

	return items.map((service, index) => {
		return (
			<View key={"advertising_"+index}>
				<Card>
					<CardItem cardBody>
						<View style={{flex: 1,flexDirection:'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: 10, paddingLeft: 25, paddingRight: 10, }}>
							<Native_Text>Visible to other devices?</Native_Text>
							<View style={{}}><Switch value={((service.advertising !== 'no')? true : false)} onValueChange={((service.advertising === 'no')? handleStartAdvertising(service) : handleStopAdvertising(service))} /></View>
						</View>
					</CardItem>
					<CardItem>
						<Left>
							<Body>
								<Text>Service ID</Text>
								<Text note>{service.serviceId}</Text>
							</Body>
						</Left>
						<Right>
						</Right>
					</CardItem>
					<CardItem>
						<Left>
							<Body>
								<Text>Endpoint Name</Text>
								<Text note>{service.endpointName}</Text>
							</Body>
						</Left>
					</CardItem>
					{(() => {
						return service.endpoints.map((endpoint) => {
							return [
								<CardItem key={"advertising_endpoint_"+endpoint.id}>
									<Left>
										<Body>
											<Text>ID</Text>
											<Text note>{endpoint.id}</Text>
											<Text>Name</Text>
											<Text note>{endpoint.name}</Text>
											<Text>State</Text>
											<Text note>{endpoint.state}</Text>
										</Body>
									</Left>
									<Right>
										{(() => {
											if (endpoint.state === "connected") {
												return (
													<Button onPress={handleDisconnectFromEndpoint(service.serviceId, endpoint)}><Text>Disconnect</Text></Button>
												);
											}
											return (
												<View>
													<Button onPress={handleAcceptConnection(service.serviceId, endpoint)}><Text>Accept</Text></Button>
													<Button onPress={handleRejectConnection(service.serviceId, endpoint)}><Text>Reject</Text></Button>
												</View>
											);
										})()}
									</Right>
								</CardItem>,
								<AudioRecorder key={"advertising_endpoint_audiorecorder_"+endpoint.id} endpoint={endpoint} onOpenMicrophone={onOpenMicrophone} onCloseMicrophone={onCloseMicrophone} />,
								<SendFile key={"advertising_endpoint_sendfile_"+endpoint.id} endpoint={endpoint} onSendFile={onSendFile} />,
								<SendMessage key={"advertising_endpoint_sendmessage_"+endpoint.id} endpoint={endpoint} onSendMessage={onSendBytes} />,
								<EndpointPayloads key={"advertising_endpoint_payloads_"+endpoint.id} endpoint={endpoint} onStartPlayingAudioStream={onStartPlayingAudioStream} onStopPlayingAudioStream={onStopPlayingAudioStream} onSaveFile={onSaveFile} onReadBytes={onReadBytes} />,
							];
						});
					})()}
				</Card>
			</View>
		);
	});
};

export default AdvertisingList;