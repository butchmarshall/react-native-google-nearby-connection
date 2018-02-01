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

const SendFile = (props) => {
	const {
		endpoint,
		onSendFile = defaultFunc,
	} = props;

	if (endpoint.state !== "connected") {
		return null;
	}

	const handleSendFile = (endpoint) => () => {
		onSendFile(endpoint);
	};

	if (!endpoint || endpoint.state !== "connected") {
		return null;
	}

	return (
		<CardItem key={"send_file_"+endpoint.id}>
			<Left>
				<Body>
					<Text>Send File</Text>
				</Body>	
			</Left>
			<Right>
				<Button onPress={handleSendFile(endpoint)}>
					<Icon name={"attach"} />
				</Button>
			</Right>
		</CardItem>
	);
};

export default SendFile;