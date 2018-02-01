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

import Nearby, { ConnectionsStatusCodes, Strategy, Payload, PayloadTransferUpdate } from 'react-native-google-nearby-connection';

const EndpointPayloads = (props) => {
	const {
		endpoint,
		onStopPlayingAudioStream = defaultFunc,
		onStartPlayingAudioStream = defaultFunc,
		onSaveFile = defaultFunc,
		onReadBytes = defaultFunc,
	} = props;

	const handleStopPlayingAudioStream = (endpoint, payload) => () => {
		onStopPlayingAudioStream(endpoint, payload);
	};
	const handleStartPlayingAudioStream = (endpoint, payload) => () => {
		onStartPlayingAudioStream(endpoint, payload);
	};
	const handleSaveFile = (endpoint, payload) => () => {
		onSaveFile(endpoint, payload);
	};
	const handleReadBytes = (endpoint, payload) => () => {
		onReadBytes(endpoint, payload);
	};

	return endpoint.payloads.map((payload, payloadId) => {
		if (payload.payloadStatus === PayloadTransferUpdate.FAILURE) {
			return null;
		}

		if (payload.payloadType === Payload.STREAM && payload.payloadStatus === PayloadTransferUpdate.IN_PROGRESS) {
			return (
				<CardItem key={"endpoint_payload_"+payloadId}>
					<Left>
						<Body>
							<Text>Play Audio</Text>
						</Body>	
					</Left>
					<Right>
						<Button onPress={((payload.playing) ? handleStopPlayingAudioStream(endpoint, payload) : handleStartPlayingAudioStream(endpoint, payload))}>
							<Icon name={((payload.playing) ? "volume-up" : "volume-off")} />
						</Button>
					</Right>
				</CardItem>
			);
		}
		else if (payload.payloadType === Payload.FILE) {
			return (
				<CardItem key={"endpoint_payload_"+payloadId}>
					<Left>
						<Body>
							<Text>Save File</Text>
						</Body>	
					</Left>
					<Right>
						<Button onPress={handleSaveFile(endpoint, payload)}>
							<Icon name={((payload.payloadStatus != PayloadTransferUpdate.SUCCESS) ? "cloud-outline" : "download")} />
						</Button>
					</Right>
				</CardItem>
			);
		}
		else if (payload.payloadType === Payload.BYTES) {
			return (
				<CardItem key={"endpoint_payload_"+payloadId}>
					<Left>
						<Body>
							<Text>Read Bytes</Text>
						</Body>	
					</Left>
					<Right>
						<Button onPress={handleReadBytes(endpoint, payload)}>
							<Icon name={((payload.payloadStatus != PayloadTransferUpdate.SUCCESS) ? "cloud-outline" : "download")} />
						</Button>
					</Right>
				</CardItem>
			);
		}

		return null;
	}).valueSeq().toJS();
};

export default EndpointPayloads;