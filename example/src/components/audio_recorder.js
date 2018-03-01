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

const defaultFunc = () => {};

const AudioRecorder = (props) => {
	const {
		endpoint,
		onCloseMicrophone = defaultFunc,
		onOpenMicrophone = defaultFunc,
	} = props;

	if (!endpoint || endpoint.state !== "connected") {
		return null;
	}

	const handleCloseMicrophone = (endpoint) => () => {
		onCloseMicrophone(endpoint);
	};
	const handleOpenMicrophone = (endpoint) => () => {
		onOpenMicrophone(endpoint);
	};

	return (
		<CardItem key={"endpoint_volume_"+endpoint.id}>
			<Left>
				<Body>
					<Text>Send Audio</Text>
				</Body>	
			</Left>
			<Right>
				<Button onPress={((endpoint.microphone_open) ? handleCloseMicrophone(endpoint) : handleOpenMicrophone(endpoint))}>
					<Icon name={((endpoint.microphone_open) ? "mic" : "mic-off")} />
				</Button>
			</Right>
		</CardItem>
	);
};

export default AudioRecorder;