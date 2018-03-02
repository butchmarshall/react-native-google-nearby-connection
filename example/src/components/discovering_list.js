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

const DiscoveringList = (props) => {
	const {
		items = [],
		onStartDiscovering = defaultFunc,
		onStopDiscovering = defaultFunc,
		onDisconnectFromEndpoint = defaultFunc,
		onConnectToEndpoint = defaultFunc,

		onCloseMicrophone = defaultFunc,
		onOpenMicrophone = defaultFunc,

		onSendFile = defaultFunc,

		onStopPlayingAudioStream = defaultFunc,
		onStartPlayingAudioStream = defaultFunc,
		onSaveFile = defaultFunc,

		onReadBytes = defaultFunc,
		onSendBytes = defaultFunc,
	} = props;

	const handleStartDiscovering = (service) => () => {
		onStartDiscovering(service);
	};
	const handleStopDiscovering = (service) => () => {
		onStopDiscovering(service);
	};

	const handleDisconnectFromEndpoint = (serviceId,endpoint) => () => {
		onDisconnectFromEndpoint(serviceId,endpoint);
	};
	const handleConnectToEndpoint = (serviceId,endpoint) => () => {
		onConnectToEndpoint(serviceId,endpoint);
	};

	return items.map((service, index) => {
		return (
			<View key={"discovering_"+index}>
				<Card>
					<CardItem cardBody>
						<View style={{flex: 1,flexDirection:'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: 10, paddingLeft: 25, paddingRight: 10, }}>
							<Native_Text>Discover other devices?</Native_Text>
							<View style={{}}><Switch value={((service.discovering !== 'no')? true : false)} onValueChange={((service.discovering === 'no')? handleStartDiscovering(service) : handleStopDiscovering(service))} /></View>
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
					{(() => {
						return service.endpoints.map((endpoint) => {
							return [
								<CardItem key={"discovering_endpoint_"+endpoint.id}>
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
											if (endpoint.state === "lost") {
												return null;
											}
											if (endpoint.state === "waiting") {
												return (
													<Text>Waiting for response...</Text>
												);
											}
											if (endpoint.state === "connected") {
												return (
													<Button onPress={handleDisconnectFromEndpoint(service.serviceId, endpoint)}><Text>Disconnect</Text></Button>
												);
											}
											return (
												<View>
													<Button onPress={handleConnectToEndpoint(service.serviceId, endpoint)}><Text>Connect</Text></Button>
												</View>
											);
										})()}
									</Right>
								</CardItem>,
								<AudioRecorder key={"discovering_endpoint_audiorecorder_"+endpoint.id} endpoint={endpoint} onOpenMicrophone={onOpenMicrophone} onCloseMicrophone={onCloseMicrophone} />,
								<SendFile key={"discovering_endpoint_sendfile_"+endpoint.id} endpoint={endpoint} onSendFile={onSendFile} />,
								<SendMessage key={"advertising_endpoint_sendmessage_"+endpoint.id} endpoint={endpoint} onSendMessage={onSendBytes} />,
								<EndpointPayloads key={"discovering_endpoint payloads_"+endpoint.id} endpoint={endpoint} onStartPlayingAudioStream={onStartPlayingAudioStream} onStopPlayingAudioStream={onStopPlayingAudioStream} onSaveFile={onSaveFile} onReadBytes={onReadBytes} />,
							];
						});
					})()}
				</Card>
			</View>
		);
	});
};

export default DiscoveringList;